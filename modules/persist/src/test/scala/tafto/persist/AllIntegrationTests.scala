package tafto.persist

import tafto.testcontainers.Postgres

import fs2.Stream
import cats.effect.*
import natchez.Trace.Implicits.noop
import tafto.db.DatabaseMigrator
import weaver.pure.*

object AllIntegrationTests extends Suite:

  val dbResource: Resource[IO, Database[IO]] = for {
    pg <- Postgres.make(dataBind = None)
    config = pg.databaseConfig
    db <- Database.make(config)
    _ <- Resource.eval(DatabaseMigrator.migrate(config))
  } yield db

  override def suitesStream: fs2.Stream[IO, Test] =
    Stream.resource(dbResource).flatMap { db =>
      // these need to run first as they expect an uninitialised super admin
      InitSuperAdminTests.tests(db) ++
        Stream(
          PgHealthServiceTests.tests(db)
        ).parJoinUnbounded
    }