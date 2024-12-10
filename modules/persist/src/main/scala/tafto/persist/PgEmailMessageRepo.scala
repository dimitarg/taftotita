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
          (x, EmailStatus.Scheduled, now)
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

  override def getScheduledIds(scheduledAtOrBefore: OffsetDateTime): F[List[EmailMessage.Id]] = database.pool.use { s =>
    s.execute(EmailMessageQueries.getScheduledIds)(scheduledAtOrBefore)
  }

  override def claim(id: EmailMessage.Id): F[Option[EmailMessage]] =
    Time[F].utc.flatMap { now =>
      updateStatusReturning(UpdateStatus.claim(id, now))
    }

  override def markAsSent(id: EmailMessage.Id): F[Boolean] =
    Time[F].utc.flatMap { now =>
      updateStatus(UpdateStatus.markAsSent(id, now))
    }

  override def markAsError(id: EmailMessage.Id, error: String): F[Boolean] = for
    now <- Time[F].utc
    result <- database.pool.use { s =>
      for
        command <- s.prepare(EmailMessageQueries.updateStatusAndError)
        completion <- command.execute((UpdateStatus.markAsError(id, now), error))
        result = wasUpdated(completion)
      yield result
    }
  yield result

  private def updateStatus(updateStatus: UpdateStatus): F[Boolean] = for result <- database.pool.use { s =>
      for
        command <- s.prepare(EmailMessageQueries.updateStatus)
        completion <- command.execute(updateStatus)
        result = wasUpdated(completion)
      yield result
    }
  yield result

  private def updateStatusReturning(updateStatus: UpdateStatus): F[Option[EmailMessage]] =
    for
      now <- Time[F].utc
      result <- database.pool.use { s =>
        for
          query <- s.prepare(EmailMessageQueries.updateStatusReturning)
          result <- query.option(updateStatus)
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

  val insertEmailEncoder = domainEmailMessageCodec *: emailStatus *: timestamptz

  def insertMessages(size: Int) = {

    sql"""
      insert into email_messages(subject, to_, cc, bcc, body, status, created_at)
      values ${insertEmailEncoder.values.list(size)}
      returning id;
    """.query(emailMessageId)
  }

  val getMessage =
    sql"""
      select subject, to_, cc, bcc, body, status from email_messages where id = ${emailMessageId};
    """.query(domainEmailMessageCodec *: emailStatus)

  val getScheduledIds = sql"""
    select id from email_messages where status = ${emailStatus} and created_at <= ${timestamptz};
  """
    .query(emailMessageId)
    .contramap[OffsetDateTime] { case createdAt => (EmailStatus.Scheduled, createdAt) }

  val updateStatus = sql"""
    with ids as (
      select id from email_messages where id=${emailMessageId} and status=${emailStatus}
      for update skip locked
    )
    update email_messages m set status=${emailStatus}, last_updated_at=${timestamptz}
    from ids
    where m.id = ids.id;
  """.command.contramap[UpdateStatus] { updateStatus =>
    (updateStatus.id, updateStatus.currentStatus, updateStatus.newStatus, updateStatus.updatedAt)
  }

  val updateStatusAndError = sql"""
    with ids as (
      select id from email_messages where id=${emailMessageId} and status=${emailStatus}
      for update skip locked
    )
    update email_messages m set status=${emailStatus}, last_updated_at=${timestamptz}, error=${text}
    from ids
    where m.id = ids.id;
  """.command.contramap[(UpdateStatus, String)] { (updateStatus, errorMessage) =>
    (updateStatus.id, updateStatus.currentStatus, updateStatus.newStatus, updateStatus.updatedAt, errorMessage)
  }

  val updateStatusReturning = sql"""
    with ids as (
      select id from email_messages where id=${emailMessageId} and status=${emailStatus}
      for update skip locked
    )
    update email_messages m set status=${emailStatus}, last_updated_at=${timestamptz}
    from ids
    where m.id = ids.id
    returning subject, to_, cc, bcc, body;
  """.query(domainEmailMessageCodec).contramap[UpdateStatus] { updateStatus =>
    (updateStatus.id, updateStatus.currentStatus, updateStatus.newStatus, updateStatus.updatedAt)
  }

}

final case class UpdateStatus private (
    id: EmailMessage.Id,
    currentStatus: EmailStatus,
    newStatus: EmailStatus,
    updatedAt: OffsetDateTime
)

object UpdateStatus:
  def claim(id: EmailMessage.Id, updatedAt: OffsetDateTime) = UpdateStatus(
    id = id,
    currentStatus = EmailStatus.Scheduled,
    newStatus = EmailStatus.Claimed,
    updatedAt = updatedAt
  )
  def markAsSent(id: EmailMessage.Id, updatedAt: OffsetDateTime) = UpdateStatus(
    id = id,
    currentStatus = EmailStatus.Claimed,
    newStatus = EmailStatus.Sent,
    updatedAt = updatedAt
  )
  def markAsError(id: EmailMessage.Id, updatedAt: OffsetDateTime) = UpdateStatus(
    id = id,
    currentStatus = EmailStatus.Claimed,
    newStatus = EmailStatus.Error,
    updatedAt = updatedAt
  )
