package tafto.domain

import cats.Eq
import tafto.util.*
import io.github.iltotore.iron.*

final case class EmailMessage(
    subject: Option[NonEmptyString],
    to: List[Email],
    cc: List[Email],
    bcc: List[Email],
    body: Option[NonEmptyString]
)

object EmailMessage:
  opaque type Id = Long :| Pure
  object Id extends RefinedTypeOps[Long, Pure, Id]
  given eq: Eq[EmailMessage] = Eq.fromUniversalEquals

enum EmailStatus:
  case Scheduled, Sent, Error
object EmailStatus:
  given eq: Eq[EmailStatus] = Eq.fromUniversalEquals
