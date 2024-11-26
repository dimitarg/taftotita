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
import tafto.domain.EmailMessage.Id
import skunk.data.Completion

final case class PgEmailMessageRepo[F[_]: Clock: MonadCancelThrow](
    database: Database[F],
    channelId: Identifier
) extends EmailMessageRepo[F]:
  override def insertMessages(messages: NonEmptyList[EmailMessage]): F[List[EmailMessage.Id]] =
    database.transact { s =>
      for
        now <- Time[F].utc
        result <- Database.batched(s)(EmailMessageQueries.insertMessages)(messages.map { x =>
          (x, EmailStatus.Scheduled, 0, now)
        })
        channel = s.channel(channelId)
        _ <- result.traverse_(x => {
          channel.notify(x.value.show)
        })
      yield result
    }

  override def getMessage(id: EmailMessage.Id): F[Option[(EmailMessage, EmailStatus)]] =
    database.pool.use { s =>
      s.option(EmailMessageQueries.getMessage)(id)
    }

  override def claim(id: Id): F[Option[EmailMessage]] =
    updateStatusReturning(id = id, status = EmailStatus.Claimed, previousStatus = EmailStatus.Scheduled)

  override def markAsSent(id: Id): F[Boolean] =
    updateStatus(id = id, status = EmailStatus.Sent, previousStatus = EmailStatus.Claimed)

  private def updateStatus(id: Id, status: EmailStatus, previousStatus: EmailStatus): F[Boolean] = for
    now <- Time[F].utc
    result <- database.pool.use { s =>
      for
        command <- s.prepare(EmailMessageQueries.updateStatus)
        completion <- command.execute((status, now, id, previousStatus))
        result = wasUpdated(completion)
      yield result
    }
  yield result

  private def updateStatusReturning(id: Id, status: EmailStatus, previousStatus: EmailStatus): F[Option[EmailMessage]] =
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

  override val insertedMessages: Stream[F, EmailMessage.Id] =
    database
      .subscribeToChannel(channelId)
      .evalMap { notification =>
        val payload = notification.value
        payload.toLongOption
          .toRight(s"Expect EmailMessage.Id, got ${payload}")
          .map(EmailMessage.Id(_))
          .orThrow[F]
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

  val updateStatus = sql"""
    update email_messages set status=${emailStatus}, last_attempted_at=${timestamptz} where id=${emailMessageId} and status=${emailStatus};
  """.command

  val updateStatusReturning = sql"""
    update email_messages set status=${emailStatus}, last_attempted_at=${timestamptz} where id=${emailMessageId} and status=${emailStatus}
    returning subject, to_, cc, bcc, body;
  """.query(domainEmailMessageCodec)

}
