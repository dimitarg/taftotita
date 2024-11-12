package tafto.rest.api.resources

import io.circe.Codec
import io.circe.generic.semiauto.*
import sttp.tapir.Schema

sealed trait ClientError

object ClientError:
  final case class Conflict(message: String) extends ClientError derives Schema, Codec
