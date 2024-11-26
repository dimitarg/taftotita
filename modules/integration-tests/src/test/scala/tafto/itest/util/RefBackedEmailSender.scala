package tafto.itest.util

import scala.concurrent.duration.FiniteDuration

import cats.implicits.*
import cats.effect.*
import tafto.domain.EmailMessage
import tafto.service.comms.EmailSender

final case class RefBackedEmailSender[F[_]: Temporal](ref: Ref[F, List[(EmailMessage.Id, EmailMessage)]])
    extends EmailSender[F]:
  override def sendEmail(id: EmailMessage.Id, email: EmailMessage): F[Unit] = ref.update(xs => (id, email) :: xs)

  val getEmails: F[List[(EmailMessage.Id, EmailMessage)]] = ref.get.map(_.reverse)

  def waitForIdleAndGetEmails(timeout: FiniteDuration): F[List[(EmailMessage.Id, EmailMessage)]] =
    repeatWhile(getEmails) {
      case (None, _)                 => true
      case (Some(previous), current) => current.length > previous.length
    }(timeout)

object RefBackedEmailSender:
  def make[F[_]: Temporal]: F[RefBackedEmailSender[F]] =
    Ref.of(List.empty[(EmailMessage.Id, EmailMessage)]).map { ref =>
      RefBackedEmailSender(ref)
    }
