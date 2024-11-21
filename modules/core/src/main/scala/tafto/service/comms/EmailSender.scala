package tafto.service.comms

import tafto.domain.EmailMessage

trait EmailSender[F[_]]:
  def sendEmail(email: EmailMessage): F[Unit]
