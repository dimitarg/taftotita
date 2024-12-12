package tafto.rest.server

import cats.effect.*
import cats.implicits.*
import tafto.rest.api.HealthEndpoints.*
import tafto.rest.api.resources.HealthResponse
import tafto.service.HealthService

final case class HealthRoutes[F[_]: Concurrent](
    healthService: HealthService[F]
):
  val getHealthRoute = getHealth.serverLogicSuccess { _ =>
    healthService.getHealth
      .as(HealthResponse("Service is healthy."))
  }
