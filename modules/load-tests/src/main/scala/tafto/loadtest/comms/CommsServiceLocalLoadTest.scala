package tafto.loadtest.comms

import cats.data.{Kleisli, NonEmptyList}
import cats.effect.std.UUIDGen
import cats.effect.{IO, IOApp, Resource}
import cats.implicits.*
import ciris.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.cats.given
import io.github.iltotore.iron.ciris.given
import io.github.iltotore.iron.constraint.numeric.Positive
import io.odin.Logger
import monocle.syntax.all.*
import org.typelevel.otel4s.oteljava.context.Context
import org.typelevel.otel4s.trace.Tracer
import tafto.db.DatabaseMigrator
import tafto.domain.*
import tafto.json.JsonStringCodecs.traceableMessageIdsStringCodec as channelCodec
import tafto.log.defaultLogger
import tafto.persist.*
import tafto.service.comms.CommsService
import tafto.service.comms.CommsService.PollingConfig
import tafto.testcontainers.*
import tafto.util.tracing.*

// find more reliable way to measure max duration as opposed to sum of trace durations

object CommsServiceLocalLoadTest extends IOApp.Simple:

  val warmupSize = 500
  val testSize = 20000

  given logger: Logger[TracedIO] = defaultLogger
  given ioLogger: Logger[IO] = defaultLogger

  val makeTestResources: Resource[TracedIO, TestResources] =
    for
      testConfig <- Resource.eval(TestConfig.load.load[TracedIO])
      containers <- Containers.make(ContainersConfig.loadTest(testConfig.poolSize)).mapK(Kleisli.liftK)
      config = containers.postgres.databaseConfig
      _ <- Resource.eval(DatabaseMigrator.migrate[TracedIO](config))

      testRunUUID <- Resource.eval(UUIDGen[TracedIO].randomUUID)
      tracingGlobalFields = Map(
        "test.uuid" -> testRunUUID.toString(),
        "pool.size" -> testConfig.poolSize.show,
        "test.pool.size" -> testConfig.testPoolSize.show
      )
      tracer <- mkHoneycombTracer(
        serviceName = "tafto-comms",
        globalFields = tracingGlobalFields
      )

      testTracer <- mkHoneycombTracer(
        serviceName = "tafto-load-tests",
        globalFields = tracingGlobalFields
      )

      channelId <- Resource.eval(PgEmailMessageRepo.defaultChannelId[TracedIO])

      (commsDb, commsService) <-
        given Tracer[TracedIO] = tracer
        for
          commsDb <- Database.make[TracedIO](config)
          emailRepo = PgEmailMessageRepo(commsDb, channelId, channelCodec)
          commsService = CommsService(emailRepo, new NoOpEmailSender[TracedIO], PollingConfig.default)
        yield (commsDb, commsService)

      (testDb, testCommsService) <-
        given Tracer[TracedIO] = testTracer
        for
          commsDb <- Database.make[TracedIO](
            config
              .focus(_.poolSize)
              .replace(testConfig.testPoolSize)
          )
          emailRepo = PgEmailMessageRepo(commsDb, channelId, channelCodec)
          commsService = CommsService(emailRepo, new NoOpEmailSender[TracedIO], PollingConfig.default)
        yield (commsDb, commsService)

      _ <- Resource.eval(
        Logger[TracedIO].info(s"Test run is $testRunUUID") >>
          Logger[TracedIO].info(
            s"Db pool size in effect is ${testConfig.poolSize}, test pool size in effect is ${testConfig.testPoolSize}"
          )
      )
    yield TestResources(
      commsService = commsService,
      commsDb = commsDb,
      testCommsService = testCommsService,
      testDb = testDb,
      testTracer = testTracer
    )

  def publishTestMessages(testResources: TestResources): IO[Unit] =
    val msg = EmailMessage(
      subject = Some("Hello there"),
      to = List(Email("foo@example.com")),
      cc = List(Email("bar@example.com")),
      bcc = List(Email("bar@example.com")),
      body = Some("General Kenobi!")
    )
    val msgs = NonEmptyList(msg, List.fill(testSize - 1)(msg))

    val result = testResources.testCommsService.scheduleEmails(msgs)

    testResources.testTracer
      .rootSpan("scheduleTestMessages")
      .surround(result)
      .void
      .run(Context.root)

  def warmup(db: Database[TracedIO], name: String)(using tracer: Tracer[TracedIO]): IO[Unit] =
    val healthService = PgHealthService(db)

    val result = for
      _ <- Logger[TracedIO].info(s"Warming up $name")
      _ <- (1 to warmupSize).toList.parTraverse_(_ => healthService.getHealth)
      _ <- Logger[TracedIO].info(s"Warmup finished: $name")
    yield ()
    // we don't need the warmups traced
    tracer.noopScope(result).run(Context.root)

  override def run: IO[Unit] =
    makeTestResources.mapK(runInRootContext).use { testResources =>

      val warmups = List(
        warmup(testResources.commsDb, "comms service database pool")(using testResources.testTracer), // todo
        warmup(testResources.testDb, "test database pool")(using testResources.testTracer)
      ).parSequence_

      val test = testResources.commsService.backfillAndRun.compile.drain.run(Context.root).background.use { handle =>
        for
          _ <- Logger[IO].info("publishing test messages ...")
          _ <- publishTestMessages(testResources)
          _ <- Logger[IO].info("published test messages.")
          _ <- handle.flatMap(_.embedError)
        yield ()
      }

      warmups >> test
    }

  final case class TestResources(
      commsDb: Database[TracedIO],
      commsService: CommsService[TracedIO],
      testCommsService: CommsService[TracedIO],
      testDb: Database[TracedIO],
      testTracer: Tracer[TracedIO]
  )

  final case class TestConfig(
      poolSize: Int :| Positive,
      testPoolSize: Int :| Positive
  )

  object TestConfig:

    val load: ConfigValue[Effect, TestConfig] =
      (
        env("POOL_SIZE")
          .as[Int :| Positive]
          .default(32),
        env("TEST_POOL_SIZE")
          .as[Int :| Positive]
          .default(10)
      ).mapN { (poolSize, testPoolSize) =>
        TestConfig(
          poolSize = poolSize,
          testPoolSize = testPoolSize
        )
      }
