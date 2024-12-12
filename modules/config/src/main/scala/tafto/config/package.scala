package tafto

import ciris.Secret
import io.github.iltotore.iron.RefinedTypeOps
import tafto.util.NonEmpty
import tafto.util.NonEmptyString

package object config:

  opaque type UserName = NonEmptyString
  object UserName extends RefinedTypeOps[String, NonEmpty, UserName]

  final case class Password(value: Secret[String])
