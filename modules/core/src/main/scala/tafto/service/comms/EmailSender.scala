package tafto.service.comms

import cats.effect.Temporal
import io.odin.Logger
import retry.RetryPolicy
import tafto.domain.EmailMessage
import tafto.service.util.Retry

trait EmailSender[F[_]]:
  def sendEmail(id: EmailMessage.Id, email: EmailMessage): F[Unit]

object EmailSender:
  def retrying[F[_]: Temporal: Logger](policy: RetryPolicy[F, Throwable])(underying: EmailSender[F]): EmailSender[F] =
    (id, email) => Retry.retrying(policy)(underying.sendEmail(id, email))
