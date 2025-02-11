package tafto.itest

import cats.effect.*
import cats.implicits.*
import fs2.Stream
import io.odin.Logger
import org.typelevel.otel4s.trace.Tracer
import tafto.db.DatabaseMigrator
import tafto.log.defaultLogger
import tafto.persist.Database
import tafto.persist.testutil.ChannelIdGenerator
import tafto.testcontainers.*
import weaver.pure.*

object AllIntegrationTests extends Suite:

  given logger: Logger[IO] = defaultLogger
  given tracer: Tracer[IO] = Tracer.Implicits.noop

  val dbResource: Resource[IO, Database[IO]] = for
    containers <- Containers.make(ContainersConfig.test)
    config = containers.postgres.databaseConfig
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
