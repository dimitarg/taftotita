package tafto.service.comms

import cats.effect.Temporal
import tafto.domain.EmailMessage
import retry.RetryPolicy
import tafto.service.util.Retry
import io.odin.Logger

trait EmailSender[F[_]]:
  def sendEmail(id: EmailMessage.Id, email: EmailMessage): F[Unit]

object EmailSender:
  def retrying[F[_]: Temporal: Logger](policy: RetryPolicy[F])(underying: EmailSender[F]): EmailSender[F] =
    (id, email) => Retry.retrying(policy)(underying.sendEmail(id, email))
