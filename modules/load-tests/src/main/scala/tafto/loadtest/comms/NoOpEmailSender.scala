package tafto.loadtest.comms

import cats.Applicative
import cats.implicits.*
import tafto.domain.EmailMessage
import tafto.service.comms.EmailSender

final case class NoOpEmailSender[F[_]: Applicative]() extends EmailSender[F]:
  override def sendEmail(id: EmailMessage.Id, email: EmailMessage): F[Unit] = ().pure[F]
