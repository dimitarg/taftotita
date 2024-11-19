package tafto.persist

import cats.implicits.*
import tafto.domain.EmailMessage
import skunk.Codec
import skunk.codec.all as SkunkCodecs
import skunk.data.Type
import java.time.OffsetDateTime

final case class PgEmailMessage(
    id: PgEmailMessage.Id,
    message: EmailMessage,
    status: EmailStatus,
    numAttempts: Int,
    error: Option[String],
    createdAt: OffsetDateTime,
    lastAttemptedAt: Option[OffsetDateTime]
)

object PgEmailMessage:
  opaque type Id = Long
  object Id:
    val codec: Codec[Id] = SkunkCodecs.int8
    extension (x: Id) inline def value: Long = x

enum EmailStatus:
  case Scheduled, Sent, Error

object EmailStatus:
  val codec: Codec[EmailStatus] = SkunkCodecs.`enum`(
    encode = _ match
      case Scheduled => "scheduled"
      case Sent      => "sent"
      case Error     => "error"
    ,
    decode = _ match
      case "scheduled" => Scheduled.some
      case "sent"      => Sent.some
      case "error"     => Error.some
      case _           => None
    ,
    tpe = Type("email_status")
  )
