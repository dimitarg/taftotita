package tafto.service.comms

import tafto.domain.*
import cats.effect.*
import fs2.Stream
import cats.data.NonEmptyList
import cats.MonadThrow
import cats.implicits.*
import cats.effect.implicits.*
import io.odin.Logger

trait CommsService[F[_]]:
  /** Delivery semantics - eventual at-least-once delivery. That is to say, if the effect executes successfully, the
    * email will have been scheduled for delivery, in a persistent way that would survive across i.e. service restart.
    */
  def scheduleEmails(messages: NonEmptyList[EmailMessage]): F[List[EmailMessage.Id]]

  def run: Stream[F, Unit]

  def backfill: F[Unit]

  def backfillAndRun(using concurrent: Concurrent[F]): F[Unit] =
    run.compile.drain.background.use { x =>
      for
        _ <- backfill
        outcome <- x
        result <- outcome.embedError
      yield result
    }

object CommsService:
  def apply[F[_]: MonadThrow: Logger](
      emailMessageRepo: EmailMessageRepo[F],
      emailSender: EmailSender[F]
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

      override val backfill: F[Unit] =
        emailMessageRepo.getScheduledIds.flatMap(emailMessageRepo.notify)
    }
