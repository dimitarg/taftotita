package tafto.persist

import tafto.domain.*
import fs2.Stream
import cats.implicits.*
import skunk.implicits.*
import skunk.codec.all.*
import tafto.persist.codecs.*
import tafto.util.*
import java.time.OffsetDateTime
import cats.effect.kernel.Clock
import cats.data.NonEmptyList
import skunk.data.Identifier
import cats.effect.kernel.MonadCancelThrow
import io.github.iltotore.iron.autoRefine
import io.github.iltotore.iron.cats.given
import tafto.service.comms.EmailMessageRepo
import skunk.data.Completion
import skunk.Session

final case class PgEmailMessageRepo[F[_]: Clock: MonadCancelThrow](
    database: Database[F],
    channelId: Identifier
) extends EmailMessageRepo[F]:
  override def scheduleMessages(messages: NonEmptyList[EmailMessage]): F[List[EmailMessage.Id]] =
    database.transact { s =>
      for
        now <- Time[F].utc
        result <- Database.batched(s)(EmailMessageQueries.insertMessages)(messages.map { x =>
          (x, EmailStatus.Scheduled, 0, now)
        })
        _ <- notify(s, result)
      yield result
    }

  private def notify(s: Session[F], ids: List[EmailMessage.Id]): F[Unit] =
    val channel = s.channel(channelId)
    ids.traverse_(x => channel.notify(x.show))

  override def getMessage(id: EmailMessage.Id): F[Option[(EmailMessage, EmailStatus)]] =
    database.pool.use { s =>
      s.option(EmailMessageQueries.getMessage)(id)
    }

  override val getScheduledIds: F[List[EmailMessage.Id]] = database.pool.use { s =>
    s.execute(EmailMessageQueries.getMessageIdsInStatus)(EmailStatus.Scheduled)
  }

  override def claim(id: EmailMessage.Id): F[Option[EmailMessage]] =
    updateStatusReturning(id = id, status = EmailStatus.Claimed, previousStatus = EmailStatus.Scheduled)

  override def markAsSent(id: EmailMessage.Id): F[Boolean] =
    updateStatus(id = id, status = EmailStatus.Sent, previousStatus = EmailStatus.Claimed)

  override def markAsError(id: EmailMessage.Id, error: String): F[Boolean] = for
    now <- Time[F].utc
    result <- database.pool.use { s =>
      for
        command <- s.prepare(EmailMessageQueries.updateStatusAndError)
        completion <- command.execute((EmailStatus.Error, now, error, 1, id, EmailStatus.Claimed))
        result = wasUpdated(completion)
      yield result
    }
  yield result

  private def updateStatus(id: EmailMessage.Id, status: EmailStatus, previousStatus: EmailStatus): F[Boolean] = for
    now <- Time[F].utc
    result <- database.pool.use { s =>
      for
        command <- s.prepare(EmailMessageQueries.updateStatus)
        completion <- command.execute((status, now, id, previousStatus))
        result = wasUpdated(completion)
      yield result
    }
  yield result

  private def updateStatusReturning(
      id: EmailMessage.Id,
      status: EmailStatus,
      previousStatus: EmailStatus
  ): F[Option[EmailMessage]] =
    for
      now <- Time[F].utc
      result <- database.pool.use { s =>
        for
          query <- s.prepare(EmailMessageQueries.updateStatusReturning)
          result <- query.option((status, now, id, previousStatus))
        yield result
      }
    yield result

  private def wasUpdated(completion: Completion) = completion match
    case Completion.Update(count) if count > 0 => true
    case _                                     => false

  override val listen: Stream[F, EmailMessage.Id] =
    database
      .subscribeToChannel(channelId)
      .evalMap { notification =>
        val payload = notification.value
        payload.toLongOption
          .toRight(s"Expect EmailMessage.Id, got ${payload}")
          .map(EmailMessage.Id(_))
          .orThrow[F]
      }

  override def notify(messages: List[EmailMessage.Id]): F[Unit] = database.pool.use { s =>
    notify(s, messages)
  }

object EmailMessageQueries {

  val domainEmailMessageCodec =
    (nonEmptyText.opt *: toList(_email) *: toList(_email) *: toList(_email) *: nonEmptyText.opt)
      .to[EmailMessage]

  val insertEmailEncoder = domainEmailMessageCodec *: emailStatus *: int4 *: timestamptz

  def insertMessages(size: Int) = {

    sql"""
      insert into email_messages(subject, to_, cc, bcc, body, status, num_attempts, created_at)
      values ${insertEmailEncoder.values.list(size)}
      returning id;
    """.query(emailMessageId)
  }

  val getMessage =
    sql"""
      select subject, to_, cc, bcc, body, status from email_messages where id = ${emailMessageId};
    """.query(domainEmailMessageCodec *: emailStatus)

  val getMessageIdsInStatus = sql"""
    select id from email_messages where status = ${emailStatus};
  """.query(emailMessageId)

  val updateStatus = sql"""
    update email_messages set status=${emailStatus}, last_attempted_at=${timestamptz} where id=${emailMessageId} and status=${emailStatus};
  """.command

  val updateStatusAndError = sql"""
    update email_messages set status=${emailStatus}, last_attempted_at=${timestamptz}, error=${text}, num_attempts = num_attempts + ${int4}
    where id=${emailMessageId} and status=${emailStatus};
  """.command

  val updateStatusReturning = sql"""
    update email_messages set status=${emailStatus}, last_attempted_at=${timestamptz} where id=${emailMessageId} and status=${emailStatus}
    returning subject, to_, cc, bcc, body;
  """.query(domainEmailMessageCodec)

}
