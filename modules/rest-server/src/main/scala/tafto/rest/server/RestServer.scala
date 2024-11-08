package tafto.rest.server

import cats.implicits.*
import cats.effect.*
import org.http4s.ember.server.EmberServerBuilder
import fs2.io.net.Network
import tafto.service.HealthService

object RestServer:
  def make[F[_]: Async: Network](
      healthService: HealthService[F]
  ): Resource[F, Unit] =
    val allRoutes = HealthRoutes[F](healthService).routes
    EmberServerBuilder
      .default[F]
      .withHttpApp(allRoutes.orNotFound)
      .build
      .void
