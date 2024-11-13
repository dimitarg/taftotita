package tafto.domain

import tafto.util.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import ciris.Secret
import tafto.util.NonEmpty

opaque type Email = NonEmptyString
object Email extends RefinedTypeOps[String, NonEmpty, Email]

type UserRoleSized = MinLength[1] & MaxLength[100]
opaque type UserRole = String :| UserRoleSized

object UserRole extends RefinedTypeOps[String, UserRoleSized, UserRole]:
  val SuperAdmin: UserRole = UserRole("tafto_super_admin")

// Do not allow for passwords of less than 12 chars
// Do not allow for passwords of more than 70 chars - currently we're using BCrypt, which has a maximum of 71 bytes input.
// This might be made stronger, f.e. check it's all ASCII, etc. We don't do that currently.
// We do not want to force special symbols on users, since they might be using long passphrases, such as 'CorrectHorseBatteryStaple'
type UserPassword = Secret[String :| SizedBetween[12, 70]]

enum PasswordHashAlgo:
  case Bcrypt

final case class HashedUserPassword(
    algo: PasswordHashAlgo,
    value: String
)
