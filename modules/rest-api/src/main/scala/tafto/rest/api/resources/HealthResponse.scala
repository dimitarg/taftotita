package tafto.rest.api.resources

import io.circe.Codec
import io.circe.generic.semiauto.*
import sttp.tapir.Schema

final case class HealthResponse(
    message: String
)

object HealthResponse:
  given codec: Codec[HealthResponse] = deriveCodec
  given schema: Schema[HealthResponse] = Schema.derived
