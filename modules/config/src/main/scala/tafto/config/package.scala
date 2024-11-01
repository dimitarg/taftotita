package tafto

import tafto.util.NonEmptyString
import ciris.Secret
import io.github.iltotore.iron.RefinedTypeOps
import tafto.util.NonEmpty

package object config:

  opaque type UserName = NonEmptyString
  object UserName extends RefinedTypeOps[String, NonEmpty, UserName]

  final case class Password(value: Secret[String])
