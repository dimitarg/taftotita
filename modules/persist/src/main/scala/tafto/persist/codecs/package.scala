package tafto.persist

import skunk.Codec
import tafto.domain.*
import tafto.util.*
import skunk.codec.all.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.skunk.*

package object codecs:
  val email: Codec[Email] = text.eimap(Email.either)(_.value)
  val userRole: Codec[UserRole] = varchar(100).eimap(UserRole.either)(_.value)
  def nonEmptyString(underlying: Codec[String]): Codec[NonEmptyString] = underlying.refined[NonEmpty]
  val nonEmptyText: Codec[NonEmptyString] = nonEmptyString(text)
