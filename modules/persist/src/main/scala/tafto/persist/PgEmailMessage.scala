package tafto.persist

import cats.implicits.*
import tafto.domain.*
import java.time.OffsetDateTime

final case class PgEmailMessage(
    id: EmailMessage.Id,
    message: EmailMessage,
    status: EmailStatus,
    numAttempts: Int,
    error: Option[String],
    createdAt: OffsetDateTime,
    lastAttemptedAt: Option[OffsetDateTime]
)
