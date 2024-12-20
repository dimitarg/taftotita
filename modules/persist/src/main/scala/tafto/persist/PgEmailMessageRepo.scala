package tafto.persist

import cats.MonadThrow
import cats.data.NonEmptyList
import cats.effect.kernel.MonadCancelThrow
import cats.implicits.*
import fs2.{Chunk, Stream}
import io.github.iltotore.iron.autoRefine
import natchez.*
import skunk.Session
import skunk.codec.all.*
import skunk.data.{Completion, Identifier}
import skunk.implicits.*
import tafto.domain.*
import tafto.domain.EmailMessage.Id
import tafto.persist.codecs.*
import tafto.persist.unsafe.*
import tafto.service.comms.EmailMessageRepo
import tafto.util.*
import tafto.util.tracing.{*, given}

import java.time.OffsetDateTime

final case class PgEmailMessageRepo[F[_]: Time: MonadCancelThrow: Trace](
    database: Database[F],
    channelId: Identifier,
    channelCodec: StringCodec[
      TraceableMessage[List[EmailMessage.Id]],
      TraceableMessage[Chunk[EmailMessage.Id]]
    ]
) extends EmailMessageRepo[F]:
  override def scheduleMessages(messages: NonEmptyList[EmailMessage]): F[List[EmailMessage.Id]] =
    span("scheduleMessages")("payload.size" -> messages.size) {
      database.transact { s =>
        for
          now <- Time[F].utc
          result <- Database.batched(s)(EmailMessageQueries.insertMessages)(messages.map { x =>
            (x, EmailStatus.Scheduled, now)
          })
          _ <- notify(s, result)
        yield result
      }
    }

  // payload must be less than 8000 bytes under default PG configuration
  // long max value is 19 digits, max value plus comma separator is 20 bytes in utf-8
  // this means we must send less than 400 messages. 350 leaves some leeway but this can be increased to 399.
  private val notifyBatchSize = 350

  private def notify(s: Session[F], ids: List[EmailMessage.Id]): F[Unit] =
    span("notify")("payload.size" -> ids.size) {
      val channel = s.channel(channelId)
      ids.grouped(notifyBatchSize).toList.traverse_ { xs =>
        for
          message <- TraceableMessage.make(xs)
          _ <- channel.notify(
            channelCodec.encoder.encode(message)
          )
        yield ()
      }
    }

  override def getMessage(id: EmailMessage.Id): F[Option[(EmailMessage, EmailStatus)]] =
    span("getMessage")("id" -> id) {
      database.pool.use { s =>
        s.option(EmailMessageQueries.getMessage)(id)
      }
    }

  override def getScheduledIds(scheduledAtOrBefore: OffsetDateTime): F[List[EmailMessage.Id]] =
    Trace[F].span("getScheduledIds") {
      database.pool.use { s =>
        s.execute(EmailMessageQueries.getScheduledIds)(scheduledAtOrBefore)
      }
    }

  override def getClaimedIds(claimedAtOrBefore: OffsetDateTime): F[List[Id]] = Trace[F].span("getClaimedIds") {
    database.pool.use { s =>
      s.execute(EmailMessageQueries.getClaimedIds)(claimedAtOrBefore)
    }
  }

  override def claim(id: EmailMessage.Id): F[Option[EmailMessage]] =
    span("claim")("id" -> id) {
      Time[F].utc.flatMap { now =>
        updateStatusReturning(UpdateStatus.claim(id, now))
      }
    }

  override def markAsSent(id: EmailMessage.Id): F[Boolean] =
    span("markAsSent")("id" -> id) {
      Time[F].utc.flatMap { now =>
        updateStatus(UpdateStatus.markAsSent(id, now))
      }
    }

  override def markAsError(id: EmailMessage.Id, error: String): F[Boolean] =
    span("markAsError")("id" -> id) {
      for
        now <- Time[F].utc
        result <- database.pool.use { s =>
          for
            command <- s.prepare(EmailMessageQueries.updateStatusAndError)
            completion <- command.execute((UpdateStatus.markAsError(id, now), error))
            result = wasUpdated(completion)
          yield result
        }
      yield result
    }

  private def updateStatus(updateStatus: UpdateStatus): F[Boolean] = for result <- database.pool.use { s =>
      val command = EmailMessageQueries.updateStatusSimple(updateStatus)
      for
        completion <- s.execute(command)
        result = wasUpdated(completion)
      yield result
    }
  yield result

  private def updateStatusReturning(updateStatus: UpdateStatus): F[Option[EmailMessage]] =
    for
      now <- Time[F].utc
      query = EmailMessageQueries.updateStatusReturningSimple(updateStatus)
      result <- database.pool.use { s =>
        s.option(query)
      }
    yield result

  private def wasUpdated(completion: Completion) = completion match
    case Completion.Update(count) if count > 0 => true
    case _                                     => false

  override val listen: Stream[F, TraceableMessage[Chunk[EmailMessage.Id]]] =
    database
      .subscribeToChannel(channelId)
      .evalMap { notification =>
        val payload = notification.value
        channelCodec.decoder.decode(payload).orThrow[F]
      }

  override def notify(messages: List[EmailMessage.Id]): F[Unit] = database.pool.use { s =>
    notify(s, messages)
  }

object EmailMessageQueries:

  val domainEmailMessageCodec =
    (nonEmptyText.opt *: toList(_email) *: toList(_email) *: toList(_email) *: nonEmptyText.opt)
      .to[EmailMessage]

  val insertEmailEncoder = domainEmailMessageCodec *: emailStatus *: timestamptz

  def insertMessages(size: Int) =
    sql"""
      insert into email_messages(subject, to_, cc, bcc, body, status, created_at)
      values ${insertEmailEncoder.values.list(size)}
      returning id;
    """.query(emailMessageId)

  val getMessage =
    sql"""
      select subject, to_, cc, bcc, body, status from email_messages where id = ${emailMessageId};
    """.query(domainEmailMessageCodec *: emailStatus)

  val getScheduledIds = sql"""
    select id from email_messages where status = ${emailStatus} and created_at <= ${timestamptz};
  """
    .query(emailMessageId)
    .contramap[OffsetDateTime] { case createdAt => (EmailStatus.Scheduled, createdAt) }

  val getClaimedIds = sql"""
    select id from email_messages where status = ${emailStatus} and updated_at <= ${timestamptz};
  """
    .query(emailMessageId)
    .contramap[OffsetDateTime] { case updatedAt => (EmailStatus.Claimed, updatedAt) }

  val updateStatusFr = sql"""
    with ids as (
      select id from email_messages where id=${emailMessageId} and status=${emailStatus}
      for update skip locked
    )
    update email_messages m set status=${emailStatus}, updated_at=${timestamptz}
    from ids
    where m.id = ids.id;
  """
    .contramap[UpdateStatus] { x =>
      (x.id, x.currentStatus, x.newStatus, x.updatedAt)
    }

  def updateStatusSimple(x: UpdateStatus) =
    updateStatusFr
      .unsafeInterpolate(x)(
        shouldQuote = List(false, true, true, true)
      )
      .command

  val updateStatusAndError = sql"""
    with ids as (
      select id from email_messages where id=${emailMessageId} and status=${emailStatus}
      for update skip locked
    )
    update email_messages m set status=${emailStatus}, updated_at=${timestamptz}, error=${text}
    from ids
    where m.id = ids.id;
  """.command.contramap[(UpdateStatus, String)] { (updateStatus, errorMessage) =>
    (updateStatus.id, updateStatus.currentStatus, updateStatus.newStatus, updateStatus.updatedAt, errorMessage)
  }

  val updateStatusReturningFr = sql"""
    with ids as (
      select id from email_messages where id=${emailMessageId} and status=${emailStatus}
      for update skip locked
    )
    update email_messages m set status=${emailStatus}, updated_at=${timestamptz}
    from ids
    where m.id = ids.id
    returning subject, to_, cc, bcc, body;
  """
    .contramap[UpdateStatus] { x =>
      (x.id, x.currentStatus, x.newStatus, x.updatedAt)
    }

  def updateStatusReturningSimple(x: UpdateStatus) =
    updateStatusReturningFr
      .unsafeInterpolate(x)(
        shouldQuote = List(false, true, true, true)
      )
      .query(domainEmailMessageCodec)

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

object PgEmailMessageRepo:
  def defaultChannelId[F[_]: MonadThrow]: F[Identifier] = ChannelId.apply("email_messages").orThrow[F]
