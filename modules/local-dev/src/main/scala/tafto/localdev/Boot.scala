package tafto.localdev

import cats.effect.*
import io.odin.*
import org.typelevel.otel4s.trace.Tracer
import tafto.crypto.*
import tafto.db.DatabaseMigrator
import tafto.persist.*
import tafto.rest.server.RestServer
import tafto.testcontainers.*

object Boot extends IOApp.Simple:
  val mkApp = for
    containers <- Containers.make(ContainersConfig.localDev)
    dbConfig = containers.postgres.databaseConfig
    _ <- Resource.eval(DatabaseMigrator.migrate[IO](dbConfig))
    given Tracer[IO] = Tracer.noop[IO]
    database <- Database.make[IO](dbConfig)
    healthService = PgHealthService(database)
    passwordHasher = PasswordHasherImpl[IO]
    userRepo = PgUserRepo(database, passwordHasher)
    _ <- RestServer.make(healthService, userRepo)
  yield ()

  val logger = consoleLogger[IO]()
  val mkAppLogged = mkApp
    .evalTap { _ =>
      logger.info("Tafto up and running.")
    }
    .onFinalize {
      logger.info("App shutdown successful, exiting.")
    }

  override def run: IO[Unit] = mkAppLogged.useForever
