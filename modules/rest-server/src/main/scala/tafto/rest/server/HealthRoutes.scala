package tafto.rest.server

import tafto.rest.api.{HealthEndpoints, HealthResponse}
import endpoints4s.http4s.server
import cats.implicits.*
import cats.effect.*
import tafto.service.HealthService
import org.http4s.HttpRoutes

final case class HealthRoutes[F[_]: Concurrent](
    healthService: HealthService[F]
) extends server.Endpoints[F]
    with HealthEndpoints
    with server.JsonEntitiesFromSchemas:
  val getHealthRoute = getHealth.implementedByEffect { _ =>
    healthService.getHealth
      .as(HealthResponse("Service is healthy."))
  }

  val routes = HttpRoutes.of(getHealthRoute)
