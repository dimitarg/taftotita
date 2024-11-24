package tafto.service.comms

import tafto.domain.EmailMessage

trait EmailSender[F[_]]:
  def sendEmail(id: EmailMessage.Id, email: EmailMessage): F[Unit]
