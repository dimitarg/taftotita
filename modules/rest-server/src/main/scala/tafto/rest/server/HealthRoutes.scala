package tafto.rest.server

import tafto.rest.api.HealthEndpoints.*
import tafto.rest.api.resources.HealthResponse
import cats.implicits.*
import cats.effect.*
import tafto.service.HealthService

final case class HealthRoutes[F[_]: Concurrent](
    healthService: HealthService[F]
):
  val getHealthRoute = getHealth.serverLogicSuccess { _ =>
    healthService.getHealth
      .as(HealthResponse("Service is healthy."))
  }
