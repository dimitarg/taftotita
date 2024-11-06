package tafto.service

import tafto.domain.*
import tafto.util.NonEmptyString

trait UserRepo[F[_]]:
  def initSuperAdmin(
      email: Email,
      fullName: Option[NonEmptyString],
      password: UserPassword
  ): F[Boolean]
