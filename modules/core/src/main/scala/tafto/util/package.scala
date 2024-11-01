package tafto

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

package object util:
  type NonEmpty = MinLength[1]
  type NonEmptyString = String :| NonEmpty

  opaque type Host = NonEmptyString
  object Host extends RefinedTypeOps[String, NonEmpty, Host]

  type ValidPortRange = GreaterEqual[0] & LessEqual[65535]
  opaque type Port = Int :| ValidPortRange
  object Port extends RefinedTypeOps[Int, ValidPortRange, Port]
