package tafto.service.comms

import fs2.Stream
import cats.data.NonEmptyList
import tafto.domain.*

trait EmailMessageRepo[F[_]]:
  def scheduleMessages(messages: NonEmptyList[EmailMessage]): F[List[EmailMessage.Id]]
  def getMessage(id: EmailMessage.Id): F[Option[(EmailMessage, EmailStatus)]]
  def getScheduledIds: F[List[EmailMessage.Id]]
  def claim(id: EmailMessage.Id): F[Option[EmailMessage]]
  def markAsSent(id: EmailMessage.Id): F[Boolean]
  def markAsError(id: EmailMessage.Id, error: String): F[Boolean]

  def listen: Stream[F, EmailMessage.Id]
  def notify(ids: List[EmailMessage.Id]): F[Unit]
