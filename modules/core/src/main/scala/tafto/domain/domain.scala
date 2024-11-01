package tafto.domain

import tafto.util.NonEmptyString
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

opaque type Email = NonEmptyString
object Email extends RefinedTypeOps[String, MinLength[1], Email]

opaque type UserRole = String :| MinLength[1] :| MaxLength[100]
