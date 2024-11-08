package tafto.rest.api

trait HealthEndpoints extends BaseEndpoints with HealthResponseSchemas {
  val getHealth: Endpoint[Unit, HealthResponse] =
    endpoint(
      get(v1 / "health"),
      ok(jsonResponse[HealthResponse])
    )
}
