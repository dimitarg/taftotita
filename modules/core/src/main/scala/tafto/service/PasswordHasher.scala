package tafto.service

import tafto.domain.*
trait PasswordHasher[F[_]]:
  def hashPassword(algo: PasswordHashAlgo, password: UserPassword): F[HashedUserPassword]
