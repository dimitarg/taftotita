package tafto.itest.util

import cats.Functor
import cats.implicits.*
import cats.effect.*
import tafto.domain.EmailMessage
import tafto.service.comms.EmailSender

final case class RefBackedEmailSender[F[_]: Functor](ref: Ref[F, List[(EmailMessage.Id, EmailMessage)]])
    extends EmailSender[F]:
  override def sendEmail(id: EmailMessage.Id, email: EmailMessage): F[Unit] = ref.update(xs => (id, email) :: xs)
  def getEmails: F[List[(EmailMessage.Id, EmailMessage)]] = ref.get.map(_.reverse)

object RefBackedEmailSender:
  def make[F[_]: Sync]: F[RefBackedEmailSender[F]] = Ref.of(List.empty[(EmailMessage.Id, EmailMessage)]).map { ref =>
    RefBackedEmailSender(ref)
  }
