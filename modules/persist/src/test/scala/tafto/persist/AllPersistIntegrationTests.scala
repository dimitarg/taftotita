package tafto.persist

import cats.effect.*
import cats.implicits.*
import fs2.Stream
import natchez.Trace
import tafto.db.DatabaseMigrator
import tafto.persist.testutil.ChannelIdGenerator
import tafto.testcontainers.*
import weaver.pure.*

object AllPersistIntegrationTests extends Suite:

  given trace: Trace[IO] = Trace.Implicits.noop

  val dbResource: Resource[IO, Database[IO]] = for
    containers <- Containers.make(ContainersConfig.test)
    config = containers.postgres.databaseConfig
    db <- Database.make(config)
    _ <- Resource.eval(DatabaseMigrator.migrate(config))
  yield db

  val channelGenResource: Resource[IO, ChannelIdGenerator[IO]] =
    Resource.eval(ChannelIdGenerator.make[IO])

  override def suitesStream: fs2.Stream[IO, Test] =
    Stream.resource((dbResource, channelGenResource).tupled).flatMap { (db, channelGen) =>
      // these need to run first as they expect an uninitialised super admin
      InitSuperAdminTests.tests(db) ++
        Stream(
          PgHealthServiceTests.tests(db),
          PgEmailMessageRepoTests.tests(db, channelGen)
        ).parJoinUnbounded
    }
