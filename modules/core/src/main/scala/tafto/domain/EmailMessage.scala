package tafto.domain

import cats.Eq
import io.github.iltotore.iron.*
import tafto.util.*

final case class EmailMessage(
    subject: Option[NonEmptyString],
    to: List[Email],
    cc: List[Email],
    bcc: List[Email],
    body: Option[NonEmptyString]
)

object EmailMessage:
  opaque type Id = Long :| Pure
  object Id extends RefinedTypeOps[Long, Pure, Id]:
    inline def unapplyList(xs: List[Id]): List[Long] = xs
  given eq: Eq[EmailMessage] = Eq.fromUniversalEquals

enum EmailStatus:
  case Scheduled, Claimed, Sent, Error
object EmailStatus:
  given eq: Eq[EmailStatus] = Eq.fromUniversalEquals
