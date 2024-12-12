package tafto.persist

import _root_.skunk.Codec
import _root_.skunk.codec.all.*
import _root_.skunk.data.Arr
import _root_.skunk.data.Type
import cats.implicits.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.skunk.*
import tafto.domain.*
import tafto.util.*

package object codecs:
  def toList[A](underlying: Codec[Arr[A]]): Codec[List[A]] = underlying.imap(_.toList)(Arr.fromFoldable(_))

  val email: Codec[Email] = text.eimap(Email.either)(_.value)
  val _email: Codec[Arr[Email]] = _text.eimap(_.traverse(Email.either))(_.map(_.value))

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

  val emailMessageId: Codec[EmailMessage.Id] = int8.imap(EmailMessage.Id(_))(_.value)

  val emailStatus: Codec[EmailStatus] = `enum`(
    encode = _ match
      case EmailStatus.Scheduled => "scheduled"
      case EmailStatus.Claimed   => "claimed"
      case EmailStatus.Sent      => "sent"
      case EmailStatus.Error     => "error"
    ,
    decode = _ match
      case "scheduled" => EmailStatus.Scheduled.some
      case "claimed"   => EmailStatus.Claimed.some
      case "sent"      => EmailStatus.Sent.some
      case "error"     => EmailStatus.Error.some
      case _           => None
    ,
    tpe = Type("email_status")
  )
