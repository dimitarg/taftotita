package tafto.itest

import cats.effect.*
import cats.implicits.*
import fs2.Stream
import io.odin.Logger
import natchez.Trace
import tafto.db.DatabaseMigrator
import tafto.log.defaultLogger
import tafto.persist.Database
import tafto.persist.testutil.ChannelIdGenerator
import tafto.testcontainers.Postgres
import weaver.pure.*

object AllIntegrationTests extends Suite:

  given trace: Trace[IO] = Trace.Implicits.noop
  given logger: Logger[IO] = defaultLogger

  val dbResource: Resource[IO, Database[IO]] = for
    pg <- Postgres.make(dataBind = None, tailLog = true)
    config = pg.databaseConfig
    db <- Database.make(config)
    _ <- Resource.eval(DatabaseMigrator.migrate(config))
  yield db

  val channelIdGen = Resource.eval(ChannelIdGenerator.make[IO])

  override def suitesStream: Stream[IO, Test] =
    Stream.resource((dbResource, channelIdGen).tupled).flatMap { (db, channelGen) =>
      // Resource-heavy suites that should be run in sequence before others
      CommsServiceDuplicationTest.tests(db, channelGen) ++
        // Suites to run in parallel
        Stream(
          CommsServiceTest.tests(db, channelGen)
        ).parJoinUnbounded

    }
