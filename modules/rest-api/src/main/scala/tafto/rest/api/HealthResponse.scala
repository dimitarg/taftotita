package tafto.rest.api

import endpoints4s.generic.JsonSchemas

final case class HealthResponse(
    message: String
)

trait HealthResponseSchemas extends JsonSchemas {
  given schema: JsonSchema[HealthResponse] = genericJsonSchema
}
