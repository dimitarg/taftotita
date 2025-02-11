package tafto.persist

import cats.MonadThrow
import cats.data.NonEmptyList
import cats.effect.kernel.MonadCancelThrow
import cats.implicits.*
import fs2.Stream
import io.github.iltotore.iron.autoRefine
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.trace.Tracer
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
import tafto.util.tracing.*

import java.time.OffsetDateTime

final case class PgEmailMessageRepo[F[_]: Time: MonadCancelThrow: Tracer](
    database: Database[F],
    channelId: Identifier,
    channelCodec: StringCodec[
      TraceableMessage[NonEmptyList[EmailMessage.Id]],
      TraceableMessage[NonEmptyList[EmailMessage.Id]]
    ]
) extends EmailMessageRepo[F]:
  override def scheduleMessages(messages: NonEmptyList[EmailMessage]): F[NonEmptyList[EmailMessage.Id]] =
    Tracer[F].span("scheduleMessages", Attribute("payload.size", messages.size.toLong)).surround {
      database.transact { s =>
        for
          now <- Time[F].utc
          result <- Database.batched(s)(EmailMessageQueries.insertMessages)(messages.map { x =>
            (x, EmailStatus.Scheduled, now)
          })
          nelResult <- result
            .toNel("Unexpected: scheduleMessages for a non-empty list returned an empty result.")
            .orThrow[F]
          traceContext <- getTraceContext[F]
          _ <- notify(s, nelResult, traceContext)
        yield nelResult
      }
    }
  // payload must be less than 8000 bytes under default PG configuration
  // long max value is 19 digits, max value plus comma separator is 20 bytes in utf-8
  // this means we must send less than 400 messages. 350 leaves some leeway but this can be increased to 399.
  private val notifyBatchSize = 350

  private def notify(s: Session[F], ids: NonEmptyList[EmailMessage.Id], traceContext: Map[String, String]): F[Unit] =
    Tracer[F].span("notify", Attribute("payload.size", ids.size.toLong)).surround {
      val channel = s.channel(channelId)
      ids.grouped(notifyBatchSize).toList.traverse_ { xs =>
        val message = TraceableMessage(traceContext, xs)
        channel.notify(channelCodec.encoder.encode(message))
      }
    }

  override def getMessage(id: EmailMessage.Id): F[Option[(EmailMessage, EmailStatus)]] =
    Tracer[F].span("getMessage", Attribute("id", id.value: Long)).surround {
      database.pool.use { s =>
        s.option(EmailMessageQueries.getMessage)(id)
      }
    }

  override def getScheduledIds(scheduledAtOrBefore: OffsetDateTime): F[List[EmailMessage.Id]] =
    Tracer[F].span("getScheduledIds").surround {
      database.pool.use { s =>
        s.execute(EmailMessageQueries.getScheduledIds)(scheduledAtOrBefore)
      }
    }

  override def getClaimedIds(claimedAtOrBefore: OffsetDateTime): F[List[Id]] =
    Tracer[F].span("getClaimedIds").surround {
      database.pool.use { s =>
        s.execute(EmailMessageQueries.getClaimedIds)(claimedAtOrBefore)
      }
    }

  override def claim(ids: NonEmptyList[EmailMessage.Id]): F[List[(EmailMessage.Id, EmailMessage)]] =
    Tracer[F].span("claim", Attribute("payload.size", ids.size.toLong)).surround {
      Time[F].utc.flatMap { now =>
        val updateStatus = UpdateStatus.claim(now)
        database.transact { s =>
          val inputBatches = ids.grouped(Database.batchSize).toList
          inputBatches
            .traverse { xs =>
              s.execute(EmailMessageQueries.updateStatusesReturning(xs.size))(xs.toList, updateStatus)
            }
            .map(_.flatten)
        }
      }
    }

  override def markAsSent(id: EmailMessage.Id): F[Boolean] =
    Tracer[F].span("markAsSent", Attribute("id", id.value: Long)).surround {
      Time[F].utc.flatMap { now =>
        updateStatus(id, UpdateStatus.markAsSent(now))
      }
    }

  override def markAsError(id: EmailMessage.Id, error: String): F[Boolean] =
    Tracer[F].span("markAsError", Attribute("id", id.value: Long)).surround {
      for
        now <- Time[F].utc
        result <- database.pool.use { s =>
          for
            command <- s.prepare(EmailMessageQueries.updateStatusAndError)
            completion <- command.execute((id, UpdateStatus.markAsError(now), error))
            result = wasUpdated(completion)
          yield result
        }
      yield result
    }

  private def updateStatus(id: EmailMessage.Id, updateStatus: UpdateStatus): F[Boolean] =
    for result <- database.pool.use { s =>
        val command = EmailMessageQueries.updateStatusSimple(id, updateStatus)
        for
          completion <- s.execute(command)
          result = wasUpdated(completion)
        yield result
      }
    yield result

  private def wasUpdated(completion: Completion) = completion match
    case Completion.Update(count) if count > 0 => true
    case _                                     => false

  override val listen: Stream[F, TraceableMessage[NonEmptyList[EmailMessage.Id]]] =
    database
      .subscribeToChannel(channelId)
      .evalMap { notification =>
        val payload = notification.value
        channelCodec.decoder.decode(payload).orThrow[F]
      }

  override def notify(messages: NonEmptyList[EmailMessage.Id]): F[Unit] = database.pool.use { s =>
    for
      k <- getTraceContext[F]
      _ <- notify(s, messages, k)
    yield ()
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
    .contramap[(EmailMessage.Id, UpdateStatus)] { (id, status) =>
      (id, status.currentStatus, status.newStatus, status.updatedAt)
    }

  def updateStatusSimple(id: EmailMessage.Id, x: UpdateStatus) =
    updateStatusFr
      .unsafeInterpolate((id, x))(
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
  """.command.contramap[(EmailMessage.Id, UpdateStatus, String)] { (id, updateStatus, errorMessage) =>
    (id, updateStatus.currentStatus, updateStatus.newStatus, updateStatus.updatedAt, errorMessage)
  }

  def updateStatusesReturning(n: Int) = sql"""
    with ids as (
      select id from email_messages where id in (${emailMessageId.list(n)}) and status=${emailStatus}
      for update skip locked
    )
    update email_messages m set status=${emailStatus}, updated_at=${timestamptz}
    from ids
    where m.id = ids.id
    returning m.id, m.subject, m.to_, m.cc, m.bcc, m.body;
  """
    .contramap[(List[EmailMessage.Id], UpdateStatus)] { (xs, x) =>
      (xs, x.currentStatus, x.newStatus, x.updatedAt)
    }
    .query(emailMessageId ~ domainEmailMessageCodec)

final case class UpdateStatus private (
    currentStatus: EmailStatus,
    newStatus: EmailStatus,
    updatedAt: OffsetDateTime
)

object UpdateStatus:
  def claim(updatedAt: OffsetDateTime) = UpdateStatus(
    currentStatus = EmailStatus.Scheduled,
    newStatus = EmailStatus.Claimed,
    updatedAt = updatedAt
  )
  def markAsSent(updatedAt: OffsetDateTime) = UpdateStatus(
    currentStatus = EmailStatus.Claimed,
    newStatus = EmailStatus.Sent,
    updatedAt = updatedAt
  )
  def markAsError(updatedAt: OffsetDateTime) = UpdateStatus(
    currentStatus = EmailStatus.Claimed,
    newStatus = EmailStatus.Error,
    updatedAt = updatedAt
  )

object PgEmailMessageRepo:
  def defaultChannelId[F[_]: MonadThrow]: F[Identifier] = ChannelId.apply("email_messages").orThrow[F]
