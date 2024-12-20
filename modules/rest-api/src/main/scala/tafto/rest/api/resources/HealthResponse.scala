package tafto.rest.api.resources

import io.circe.Codec
import sttp.tapir.Schema

final case class HealthResponse(
    message: String
) derives Schema,
      Codec
