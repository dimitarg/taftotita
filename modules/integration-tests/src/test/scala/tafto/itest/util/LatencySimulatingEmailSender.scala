package tafto.itest.util

import cats.effect.Temporal
import cats.effect.std.Random
import cats.implicits.*
import tafto.domain.EmailMessage
import tafto.service.comms.EmailSender

import scala.concurrent.duration.*

final case class LatencySimulatingEmailSender[F[_]: Temporal, Underlying <: EmailSender[F]](
    underlying: EmailSender[F],
    random: Random[F],
    maxLatency: FiniteDuration
) extends EmailSender[F]:
  override def sendEmail(id: EmailMessage.Id, email: EmailMessage): F[Unit] = for
    result <- underlying.sendEmail(id, email)
    sleepTime <- random.nextLongBounded(maxLatency.toMillis + 1)
    _ <- Temporal[F].sleep(sleepTime.millis)
  yield result
