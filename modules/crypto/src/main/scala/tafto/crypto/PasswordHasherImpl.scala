package tafto.crypto

import cats.implicits.*
import cats.effect.*
import tafto.service.PasswordHasher
import tafto.domain.*
import tsec.passwordhashers.jca.BCrypt

final class PasswordHasherImpl[F[_]: Sync] extends PasswordHasher[F]:
  override def hashPassword(algo: PasswordHashAlgo, password: UserPassword): F[HashedUserPassword] = algo match
    case PasswordHashAlgo.Bcrypt => bcryptHashPassword(password)

  def bcryptHashPassword(password: UserPassword): F[HashedUserPassword] =
    BCrypt.hashpw[F](password.value: String).map { x =>
      HashedUserPassword(PasswordHashAlgo.Bcrypt, x)
    }
