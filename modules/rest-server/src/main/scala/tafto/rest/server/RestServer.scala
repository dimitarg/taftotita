package tafto.rest.server

import cats.implicits.*
import cats.effect.*
import org.http4s.ember.server.EmberServerBuilder
import sttp.tapir.server.http4s.Http4sServerInterpreter
import fs2.io.net.Network
import tafto.service.HealthService

object RestServer:
  def make[F[_]: Async: Network](
      healthService: HealthService[F]
  ): Resource[F, Unit] =
    val healthRoutes = HealthRoutes[F](healthService)
    val allRoutes = Http4sServerInterpreter[F]().toRoutes(
      List(
        healthRoutes.getHealthRoute
      )
    )
    EmberServerBuilder
      .default[F]
      .withHttpApp(allRoutes.orNotFound)
      .build
      .void
