package tafto.persist

import cats.implicits.*
import skunk.Codec
import tafto.domain.*
import tafto.util.*
import skunk.codec.all.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.skunk.*
import _root_.skunk.data.Type

package object codecs:
  val email: Codec[Email] = text.eimap(Email.either)(_.value)
  val userRole: Codec[UserRole] = varchar(100).eimap(UserRole.either)(_.value)
  def nonEmptyString(underlying: Codec[String]): Codec[NonEmptyString] = underlying.refined[NonEmpty]
  val nonEmptyText: Codec[NonEmptyString] = nonEmptyString(text)

  // These must be kept in sync with the .sql schema definition.
  // The mapping is done in the codec layer, and done manually on purpose,
  // so that renames of enum values in the scala code don't break database serialisation / deserialisation.
  val passwordHashAlgo: Codec[PasswordHashAlgo] = `enum`(
    encode = _ match
      case PasswordHashAlgo.Bcrypt => "bcrypt"
    ,
    decode = _ match
      case "bcrypt" => PasswordHashAlgo.Bcrypt.some
      case _        => None
    ,
    tpe = Type("password_hash_algo")
  )

  val hashedUserPassword: Codec[HashedUserPassword] =
    (passwordHashAlgo ~ text).to[HashedUserPassword]
