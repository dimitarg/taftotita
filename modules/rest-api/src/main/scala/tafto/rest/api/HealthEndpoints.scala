package tafto.rest.api
import tafto.rest.api.resources.*
import BaseEndpoint.*
import sttp.tapir.*
import sttp.tapir.json.circe.*

object HealthEndpoints {
  val getHealth: Endpoint[Unit, Unit, Unit, HealthResponse, Any] =
    base(name = "getHealth", "Healthcheck for Tafto system")
      .in("health")
      .get
      .out(jsonBody[HealthResponse])

}
