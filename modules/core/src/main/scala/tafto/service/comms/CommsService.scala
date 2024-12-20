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
              summon[EntryPoint[F]].root("processChunk").use { root =>
                val result = Trace[F].put("payload.size" -> xs.size) >>
                  xs.parTraverse(processMessage)
                Local[F, Span[F]].scope(result)(root)
              }
            }
          )
          .onFinalize {
            Logger[F].info("Exiting email consumer stream.")
          }

      private def processMessage(id: EmailMessage.Id): F[Unit] =
        span("processMessage")("id" -> id) {
          for
            _ <- Logger[F].debug(s"Processing message $id.")
            maybeMessage <- emailMessageRepo.claim(id)
            _ <- maybeMessage match
              case None =>
                Logger[F].debug(
                  s"Could not claim message $id for sending as it was already claimed by another process, or does not exist."
                )
              case Some(message) =>
                processClaimedMessage(id, message)
          yield ()
        }

      private def processClaimedMessage(id: EmailMessage.Id, message: EmailMessage): F[Unit] =
        for
          sendEmailResult <- emailSender.sendEmail(id, message).attempt
          _ <- sendEmailResult.fold(markAsError(id, _), _ => markAsSent(id))
        yield ()

      private def markAsSent(id: EmailMessage.Id): F[Unit] =
        emailMessageRepo
          .markAsSent(id)
          .flatTap {
            case true => ().pure[F]
            case false =>
              Logger[F].error(
                s"Duplicate delivery detected. Email message $id sent but already marked as sent by another process."
              )
          }
          .void

      private def markAsError(id: EmailMessage.Id, error: Throwable): F[Unit] =
        for
          _ <- Logger[F].error(s"Error when sending message ${id}", error)
          wasMarked <- emailMessageRepo.markAsError(id, error.getMessage())
          _ <-
            if wasMarked then ().pure[F]
            else Logger[F].warn(s"Could not mark message $id as error, was it rescheduled in the meantime?")
        yield ()

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
            _ <- claimedIds.traverse(reprocessClaimedMessage)
          yield ()
        }

      private def reprocessClaimedMessage(id: EmailMessage.Id): F[Unit] = for
        msg <- emailMessageRepo.getMessage(id)
        result <- msg.fold {
          Logger[F].warn(s"Could not reprocess claimed message with id $id as it was not found")
        } { case (message, status) =>
          if status === EmailStatus.Claimed then processClaimedMessage(id, message)
          else
            Logger[F].debug(
              s"Will not reprocess message with id $id as it's no longer claimed, current status is $status"
            )
        }
      yield result

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
