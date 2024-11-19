package tafto.service

import tafto.domain.EmailMessage

trait CommsService[F[_]]:
  /** Delivery semantics - eventual at-least-once delivery. That is to say, if the effect executes successfully, the
    * email will have been scheduled for delivery, in a persistent way that would survive across i.e. service restart.
    */
  def sendEmail(message: EmailMessage): F[Unit]
