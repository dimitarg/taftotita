package tafto.service.comms

import cats.Parallel
import cats.data.NonEmptyList
import cats.effect.*
import cats.implicits.*
import cats.mtl.Local
import fs2.Stream
import io.odin.Logger
import natchez.{EntryPoint, Span, Trace}
import tafto.domain.*
import tafto.util.Time
import tafto.util.tracing.given
import tafto.util.tracing.{SpanLocal, span}

import scala.concurrent.duration.*

trait CommsService[F[_]]:

  /** Delivery semantics - eventual at-least-once delivery. That is to say, if the effect executes successfully, the
    * email will have been scheduled for delivery, in a persistent way that would survive across i.e. service restart.
    */
  def scheduleEmails(messages: NonEmptyList[EmailMessage]): F[List[EmailMessage.Id]]

  def run: Stream[F, Unit]

  def pollForScheduledMessages: Stream[F, Unit]

  def pollForClaimedMessages: Stream[F, Unit]

  def backfillAndRun(using c: Concurrent[F]): Stream[F, Unit] =
    Stream(pollForScheduledMessages, pollForClaimedMessages).parJoinUnbounded
      .concurrently(run)

object CommsService:
  def apply[F[_]: Temporal: Parallel: Logger: Trace: EntryPoint: SpanLocal](
      emailMessageRepo: EmailMessageRepo[F],
      emailSender: EmailSender[F],
      pollingConfig: PollingConfig
  ): CommsService[F] =
    new CommsService[F]:

      override def scheduleEmails(messages: NonEmptyList[EmailMessage]): F[List[EmailMessage.Id]] =
        emailMessageRepo.scheduleMessages(messages)

      override def run: Stream[F, Unit] =
        emailMessageRepo.listen
          .flatMap(msg =>
            val xs = msg.payload
            Stream.evalUnChunk {
              // summon[EntryPoint[F]].continueOrElseRoot("processChunk", Kernel(msg.kernel)).use { root =>
              // continuing the producer span in the consumer creates traces that are unusably large in the honeycomb UI
              // TODO upgrade skunk to use otel4s
              // TODO experiment with creating a span link instead
              summon[EntryPoint[F]].root("processChunk").use { root =>
                val result = Trace[F].put("payload.size" -> xs.size) >>
                  xs.parTraverse(processMessage)
                Local[F, Span[F]].scope(result)(root)
              }
            }
          )
          .evalMap(traceAndLog)
          .onFinalize {
            Logger[F].info("Exiting email consumer stream.")
          }

      private def processMessage(id: EmailMessage.Id): F[MessageProcessingResult] =
        span("processMessage")("id" -> id) {
          for
            _ <- Logger[F].debug(s"Processing message $id.")
            maybeMessage <- emailMessageRepo.claim(id)
            result <- maybeMessage match
              case None =>
                MessageProcessingResult.CouldNotClaim(id).pure[F]
              case Some(message) =>
                processClaimedMessage(id, message)
          yield result
        }

      private def processClaimedMessage(id: EmailMessage.Id, message: EmailMessage): F[MessageProcessingResult] =
        for
          sendEmailResult <- emailSender.sendEmail(id, message).attempt
          result <- sendEmailResult.fold(markAsError(id, _), _ => markAsSent(id))
        yield result

      private def markAsSent(id: EmailMessage.Id): F[MessageProcessingResult] =
        emailMessageRepo
          .markAsSent(id)
          .map {
            case true  => MessageProcessingResult.Marked(id, error = None)
            case false => MessageProcessingResult.CouldNotMark(id, error = None)
          }

      private def markAsError(id: EmailMessage.Id, error: Throwable): F[MessageProcessingResult] =
        emailMessageRepo.markAsError(id, error.getMessage()).map {
          case true  => MessageProcessingResult.Marked(id, error = error.some)
          case false => MessageProcessingResult.CouldNotMark(id, error = error.some)
        }

      override val pollForScheduledMessages: Stream[F, Unit] =
        Stream.fixedRateStartImmediately(pollingConfig.forScheduled.pollingInterval).evalMap { _ =>
          for
            now <- Time[F].utc
            scheduledIds <- emailMessageRepo
              .getScheduledIds(now.minusNanos(pollingConfig.forScheduled.messageAge.toNanos))
            _ <- emailMessageRepo.notify(scheduledIds)
          yield ()
        }

      override val pollForClaimedMessages: Stream[F, Unit] =
        Stream.fixedRateStartImmediately(pollingConfig.forClaimed.pollingInterval).evalMap { _ =>
          for
            now <- Time[F].utc
            claimedIds <- emailMessageRepo.getClaimedIds(now.minusNanos(pollingConfig.forClaimed.timeToLive.toNanos))
            _ <- claimedIds.traverse_(reprocessClaimedMessage >=> traceAndLog)
          yield ()
        }

      private def reprocessClaimedMessage(id: EmailMessage.Id): F[MessageProcessingResult] =
        summon[EntryPoint[F]].root("processChunk").use { root =>
          val result = for
            msg <- emailMessageRepo.getMessage(id)
            result <- msg.fold {
              MessageProcessingResult.CannotReprocess_NotFound(id).pure[F]
            } { case (message, status) =>
              if status === EmailStatus.Claimed then processClaimedMessage(id, message)
              else MessageProcessingResult.CannotReprocess_NoLongerClaimed(id, status).pure[F]
            }
          yield result
          Local[F, Span[F]].scope(result)(root)
        }

      private def traceAndLog(x: MessageProcessingResult): F[Unit] = for
        _ <- Trace[F].put("id" -> x.id)
        _ <- x match
          case MessageProcessingResult.CouldNotClaim(id) =>
            Trace[F].put("processing.result.type" -> "CouldNotClaim") >>
              Logger[F].debug(s"Could not claim message $id, it may have been claimed by another process.")
          case MessageProcessingResult.Marked(id, maybeError) =>
            Trace[F].put("processing.result.type" -> "Marked") >>
              maybeError.traverse_(traceAndLogEmailError(id, _))
          case MessageProcessingResult.CouldNotMark(id, maybeError) =>
            Trace[F].put("processing.result.type" -> "CouldNotMark") >>
              maybeError.traverse_(traceAndLogEmailError(id, _)) >>
              Logger[F].warn(s"Could not mark message $id as processed, possible duplicate delivery detected!")
          case MessageProcessingResult.CannotReprocess_NotFound(id) =>
            Trace[F].put("processing.result.type" -> "CannotReprocess_NotFound") >>
              Logger[F].error(s"Message $id due to be reprocessed was not found!")
          case MessageProcessingResult.CannotReprocess_NoLongerClaimed(id, newStatus) =>
            Trace[F].put("processing.result.type" -> "CannotReprocess_NotFound") >>
              Trace[F].put("processing.result.newStatus" -> newStatus.toString()) >>
              Logger[F].debug(
                s"Message $id due to be reprocessed is no longer claimed, new status is $newStatus, skipping."
              )
      yield ()

      private def traceAndLogEmailError(id: EmailMessage.Id, error: Throwable): F[Unit] = for
        _ <- Logger[F].warn(s"Error sending email $id", error)
        _ <- Trace[F].put("email.error" -> error.getMessage())
      yield ()

  final case class PollingConfig(
      forScheduled: ScheduledMessagesPollingConfig,
      forClaimed: ClaimedMessagesPollingConfig
  )

  object PollingConfig:
    val default: PollingConfig = PollingConfig(
      forScheduled = ScheduledMessagesPollingConfig(
        messageAge = 1.minute,
        pollingInterval = 30.seconds
      ),
      forClaimed = ClaimedMessagesPollingConfig(
        timeToLive = 30.seconds,
        pollingInterval = 30.seconds
      )
    )

  final case class ScheduledMessagesPollingConfig(
      messageAge: FiniteDuration,
      pollingInterval: FiniteDuration
  )

  final case class ClaimedMessagesPollingConfig(
      timeToLive: FiniteDuration,
      pollingInterval: FiniteDuration
  )
