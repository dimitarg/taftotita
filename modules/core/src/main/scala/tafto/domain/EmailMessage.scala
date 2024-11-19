package tafto.domain

import tafto.util.*

final case class EmailMessage(
    subject: Option[NonEmptyString],
    to: List[Email],
    cc: List[Email],
    bcc: List[Email],
    body: Option[NonEmptyString]
)
