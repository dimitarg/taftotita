package tafto.itest.util

import cats.MonadThrow
import cats.implicits.*
import cats.effect.{Ref, Sync}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*
import tafto.domain.*
import tafto.service.comms.EmailSender

final case class FlakyEmailSender[F[_]: MonadThrow, Underlying <: EmailSender[F]] private (
    underlying: Underlying,
    private val count: Ref[F, Int],
    private val timesToFail: Int :| Positive
) extends EmailSender[F]:
  override def sendEmail(id: EmailMessage.Id, email: EmailMessage): F[Unit] =
    count.updateAndGet(_ + 1).flatMap { cnt =>
      if (cnt > timesToFail) {
        underlying.sendEmail(id, email)
      } else {
        MonadThrow[F].raiseError(new RuntimeException("Flaky email sender."))
      }
    }

object FlakyEmailSender:
  def make[F[_]: Sync](timesToFail: Int :| Positive): F[FlakyEmailSender[F, RefBackedEmailSender[F]]] = for
    underlying <- RefBackedEmailSender.make[F]
    count <- Ref.of(0)
  yield FlakyEmailSender(
    underlying = underlying,
    timesToFail = timesToFail,
    count = count
  )
