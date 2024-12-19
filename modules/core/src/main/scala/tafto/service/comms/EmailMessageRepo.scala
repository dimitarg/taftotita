package tafto.service.comms

import cats.data.NonEmptyList
import fs2.{Chunk, Stream}
import tafto.domain.*

import java.time.OffsetDateTime

trait EmailMessageRepo[F[_]]:

  def scheduleMessages(messages: NonEmptyList[EmailMessage]): F[List[EmailMessage.Id]]
  def claim(id: EmailMessage.Id): F[Option[EmailMessage]]
  def markAsSent(id: EmailMessage.Id): F[Boolean]
  def markAsError(id: EmailMessage.Id, error: String): F[Boolean]

  def getMessage(id: EmailMessage.Id): F[Option[(EmailMessage, EmailStatus)]]
  def getScheduledIds(scheduledAtOrBefore: OffsetDateTime): F[List[EmailMessage.Id]]
  def getClaimedIds(claimedAtOrBefore: OffsetDateTime): F[List[EmailMessage.Id]]

  def listen: Stream[F, Chunk[EmailMessage.Id]]
  def notify(ids: List[EmailMessage.Id]): F[Unit]
