package tafto.itest.util

import cats.effect.*
import cats.implicits.*
import tafto.domain.EmailMessage
import tafto.service.comms.EmailSender

import scala.concurrent.duration.FiniteDuration

final case class RefBackedEmailSender[F[_]: Sync](ref: Ref[F, List[(EmailMessage.Id, EmailMessage)]])
    extends EmailSender[F]:
  override def sendEmail(id: EmailMessage.Id, email: EmailMessage): F[Unit] =
    ref.update(xs => (id, email) :: xs)

  val getEmails: F[List[(EmailMessage.Id, EmailMessage)]] =
    ref.get
      .map(_.reverse)

  def waitForIdleAndGetEmails(
      timeout: FiniteDuration
  )(using temporal: Temporal[F]): F[List[(EmailMessage.Id, EmailMessage)]] =
    repeatWhile(getEmails) {
      case (None, _)                 => true
      case (Some(previous), current) => current.length > previous.length
    }(timeout)

object RefBackedEmailSender:
  def make[F[_]: Sync]: F[RefBackedEmailSender[F]] =
    Ref.of(List.empty[(EmailMessage.Id, EmailMessage)]).map { ref =>
      RefBackedEmailSender(ref)
    }
