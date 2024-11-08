package tafto.localdev

import cats.effect.IOApp
import cats.effect.IO
import tafto.testcontainers.Containers
import tafto.persist.*
import natchez.Trace.Implicits.noop
import tafto.rest.server.RestServer

object Boot extends IOApp.Simple:

  val mkApp = for
    containers <- Containers.make
    dbConfig = containers.postgres.databaseConfig
    database <- Database.make[IO](dbConfig)
    healthService = PgHealthService(database)
    _ <- RestServer.make(healthService)
  yield ()

  override def run: IO[Unit] = mkApp.useForever
