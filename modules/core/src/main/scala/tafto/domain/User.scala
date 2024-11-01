package tafto.domain

import tafto.util.NonEmptyString

final case class User(
    email: Email,
    fullName: Option[NonEmptyString],
    roles: List[UserRole]
)
