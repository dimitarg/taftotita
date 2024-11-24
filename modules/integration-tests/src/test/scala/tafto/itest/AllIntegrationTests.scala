package tafto.itest

import cats.effect.*
import fs2.Stream
import weaver.pure.*
import tafto.persist.Database
import tafto.testcontainers.Postgres
import natchez.Trace.Implicits.noop
import tafto.db.DatabaseMigrator

object AllIntegrationTests extends Suite:

  val dbResource: Resource[IO, Database[IO]] = for {
    pg <- Postgres.make(dataBind = None, tailLog = true)
    config = pg.databaseConfig
    db <- Database.make(config)
    _ <- Resource.eval(DatabaseMigrator.migrate(config))
  } yield db

  override def suitesStream: Stream[IO, Test] =
    Stream.resource(dbResource).flatMap { db =>
      CommsServiceDuplicationTest.tests(db)
    }
