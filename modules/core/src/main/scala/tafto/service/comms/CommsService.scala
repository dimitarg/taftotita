package tafto.service.comms

import scala.concurrent.duration.*

import tafto.domain.*
import cats.effect.*
import fs2.Stream
import cats.data.NonEmptyList
import cats.implicits.*
import cats.effect.implicits.*
import io.odin.Logger
import tafto.util.Time

trait CommsService[F[_]]:
  /** Delivery semantics - eventual at-least-once delivery. That is to say, if the effect executes successfully, the
    * email will have been scheduled for delivery, in a persistent way that would survive across i.e. service restart.
    */
  def scheduleEmails(messages: NonEmptyList[EmailMessage]): F[List[EmailMessage.Id]]

  def run: Stream[F, Unit]

  def pollForScheduledMessages: Stream[F, Unit]

  def backfillAndRun(using c: Concurrent[F]): F[Unit] =
    run.compile.drain.background.use { x =>
      for
        _ <- pollForScheduledMessages.compile.drain
        outcome <- x
        result <- outcome.embedError
      yield result
    }

object CommsService:
  def apply[F[_]: Temporal: Logger](
      emailMessageRepo: EmailMessageRepo[F],
      emailSender: EmailSender[F],
      pollingConfig: PollingConfig
  ): CommsService[F] =
    new CommsService[F] {

      override def scheduleEmails(messages: NonEmptyList[EmailMessage]): F[List[EmailMessage.Id]] =
        emailMessageRepo.scheduleMessages(messages)

      override def run: Stream[F, Unit] =
        emailMessageRepo.listen
          .evalMap(processMessage)
          .onFinalize {
            Logger[F].info("Exiting email consumer stream.")
          }

      private def processMessage(id: EmailMessage.Id): F[Unit] =
        for
          _ <- Logger[F].debug(s"Processing message $id.")
          maybeMessage <- emailMessageRepo.claim(id)
          _ <- maybeMessage match
            case None =>
              Logger[F].debug(
                s"Could not claim message $id for sending as it was already claimed by another process, or does not exist."
              )
            case Some(message) =>
              for
                sendEmailResult <- emailSender.sendEmail(id, message).attempt
                _ <- sendEmailResult.fold(markAsError(id, _), _ => markAsSent(id))
              yield ()
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
            if (wasMarked) {
              ().pure[F]
            } else {
              Logger[F].warn(s"Could not mark message $id as error, was it rescheduled in the meantime?")
            }
        yield ()

      override val pollForScheduledMessages: Stream[F, Unit] =
        Stream.fixedRateStartImmediately(30.seconds).evalMap { _ =>
          for
            now <- Time[F].utc
            scheduledIds <- emailMessageRepo.getScheduledIds(now.minusMinutes(1))
            _ <- emailMessageRepo.notify(scheduledIds)
          yield ()
        }
    }

final case class PollingConfig(
    forScheduled: ScheduledMessagesPollingConfig
)

object PollingConfig:
  val default: PollingConfig = PollingConfig(
    forScheduled = ScheduledMessagesPollingConfig(
      messageAge = 1.minute,
      pollingInterval = 30.seconds
    )
  )

final case class ScheduledMessagesPollingConfig(
    messageAge: FiniteDuration,
    pollingInterval: FiniteDuration
)
