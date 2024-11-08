package tafto.localdev

import cats.effect.*
import tafto.testcontainers.*
import tafto.persist.*
import natchez.Trace.Implicits.noop
import tafto.rest.server.RestServer
import tafto.db.DatabaseMigrator

object Boot extends IOApp.Simple:

  val mkApp = for
    containers <- Containers.make(ContainersConfig.localDev)
    dbConfig = containers.postgres.databaseConfig
    _ <- Resource.eval(DatabaseMigrator.migrate[IO](dbConfig))
    database <- Database.make[IO](dbConfig)
    healthService = PgHealthService(database)
    _ <- RestServer.make(healthService)
  yield ()

  override def run: IO[Unit] = mkApp.useForever
