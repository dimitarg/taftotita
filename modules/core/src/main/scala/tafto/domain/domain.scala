package tafto.domain

import tafto.util.NonEmptyString
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import ciris.Secret

opaque type Email = NonEmptyString
object Email extends RefinedTypeOps[String, MinLength[1], Email]

type UserRoleSized = MinLength[1] & MaxLength[100]
opaque type UserRole = String :| UserRoleSized

object UserRole extends RefinedTypeOps[String, UserRoleSized, UserRole]:
  val SuperAdmin: UserRole = UserRole("tafto_super_admin")

type UserPassword = Secret[NonEmptyString]
