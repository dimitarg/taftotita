package tafto.itest

import cats.effect.*
import fs2.Stream
import weaver.pure.*
import tafto.persist.Database
import tafto.testcontainers.Postgres
import natchez.Trace
import tafto.db.DatabaseMigrator
import tafto.log.defaultLogger
import io.odin.Logger

object AllIntegrationTests extends Suite:

  given trace: Trace[IO] = Trace.Implicits.noop
  given logger: Logger[IO] = defaultLogger

  val dbResource: Resource[IO, Database[IO]] = for {
    pg <- Postgres.make(dataBind = None, tailLog = true)
    config = pg.databaseConfig
    db <- Database.make(config)
    _ <- Resource.eval(DatabaseMigrator.migrate(config))
  } yield db

  override def suitesStream: Stream[IO, Test] =
    Stream.resource(dbResource).flatMap { db =>
      // Resource-heavy suites that should be run in sequence before others
      CommsServiceDuplicationTest.tests(db) ++
        // Suites to run in parallel
        Stream(
          CommsServiceTest.tests(db)
        ).parJoinUnbounded

    }
