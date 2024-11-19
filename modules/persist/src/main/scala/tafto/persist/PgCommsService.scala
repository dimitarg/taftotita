package tafto.persist

import skunk.data.Identifier

final case class PgCommsService[F[_]]()

object PgCommsService {
  val channelId: Either[String, Identifier] = Identifier.fromString("email_messages")
}
