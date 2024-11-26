package tafto.service.comms

import fs2.Stream
import cats.data.NonEmptyList
import tafto.domain.*

trait EmailMessageRepo[F[_]]:
  def insertMessages(messages: NonEmptyList[EmailMessage]): F[List[EmailMessage.Id]]
  def getMessage(id: EmailMessage.Id): F[Option[(EmailMessage, EmailStatus)]]
  def insertedMessages: Stream[F, EmailMessage.Id]
  def claim(id: EmailMessage.Id): F[Option[EmailMessage]]
  def markAsSent(id: EmailMessage.Id): F[Boolean]
