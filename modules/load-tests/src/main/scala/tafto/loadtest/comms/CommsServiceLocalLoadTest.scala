package tafto.loadtest.comms

import cats.data.{Kleisli, NonEmptyList}
import cats.effect.std.UUIDGen
import cats.effect.{IO, IOApp, Resource}
import cats.implicits.*
import io.github.iltotore.iron.*
import io.odin.Logger
import natchez.EntryPoint
import natchez.mtl.given
import natchez.noop.NoopSpan
import tafto.db.DatabaseMigrator
import tafto.domain.*
import tafto.log.defaultLogger
import tafto.persist.*
import tafto.service.comms.CommsService
import tafto.service.comms.CommsService.PollingConfig
import tafto.testcontainers.*
import tafto.util.tracing.*

// find more reliable way to measure max duration as opposed to sum of trace durations
// consider xmx8g

object CommsServiceLocalLoadTest extends IOApp.Simple:

  given logger: Logger[TracedIO] = defaultLogger
  given ioLogger: Logger[IO] = defaultLogger

  val makeTestResources: Resource[TracedIO, TestResources] =
    for
      containers <- Containers.make(ContainersConfig.loadTest).mapK(Kleisli.liftK)
      config = containers.postgres.databaseConfig
      _ <- Resource.eval(DatabaseMigrator.migrate[TracedIO](config))
      commsDb <- Database.make[TracedIO](config)
      testDb <- Database.make[TracedIO](config)
      testRunUUID <- Resource.eval(UUIDGen[TracedIO].randomUUID)
      tracingGlobalFields = Map("test.uuid" -> testRunUUID.toString())
      commsEp <- honeycombEntryPoint[TracedIO](
        serviceName = "tafto-comms",
        globalFields = tracingGlobalFields
      )
      testEp <- honeycombEntryPoint[IO]("tafto-load-tests", globalFields = tracingGlobalFields).mapK(Kleisli.liftK)

      channelId <- Resource.eval(PgEmailMessageRepo.defaultChannelId[TracedIO])

      commsService =
        given EntryPoint[TracedIO] = commsEp
        val emailRepo = PgEmailMessageRepo(commsDb, channelId)
        CommsService(emailRepo, new NoOpEmailSender[TracedIO], PollingConfig.default)

      testCommsService =
        given EntryPoint[TracedIO] = testEp.mapK(Kleisli.liftK)
        val testEmailRepo = PgEmailMessageRepo(testDb, channelId)
        CommsService(testEmailRepo, new NoOpEmailSender[TracedIO], PollingConfig.default)
    yield TestResources(
      commsService = commsService,
      commsDb = commsDb,
      testCommsService = testCommsService,
      testDb = testDb,
      testEntryPoint = testEp
    )

  def publishTestMessages(testResources: TestResources, testSize: Int): IO[Unit] =
    val msg = EmailMessage(
      subject = Some("Hello there"),
      to = List(Email("foo@example.com")),
      cc = List(Email("bar@example.com")),
      bcc = List(Email("bar@example.com")),
      body = Some("General Kenobi!")
    )
    val msgs = NonEmptyList(msg, List.fill(testSize - 1)(msg))

    val result = testResources.testCommsService.scheduleEmails(msgs)

    testResources.testEntryPoint.root("scheduleTestMessages").use { span =>
      result.run(span).void
    }

  def warmup(db: Database[TracedIO], name: String): IO[Unit] =
    val healthService = PgHealthService(db)
    val result = (1 to 50).toList.parTraverse_(_ => healthService.getHealth)

    for
      _ <- Logger[IO].info(s"Warming up $name")
      _ <- result.run(NoopSpan())
      _ <- Logger[IO].info(s"Warmup finished: $name")
    yield ()

  override def run: IO[Unit] =
    makeTestResources.mapK(withNoSpan).use { testResources =>

      val warmups = List(
        warmup(testResources.commsDb, "comms service database pool"),
        warmup(testResources.testDb, "test database pool")
      ).parSequence

      val test = testResources.commsService.backfillAndRun.compile.drain.run(NoopSpan()).background.use { handle =>
        for
          _ <- Logger[IO].info("publishing test messages ...")
          _ <- publishTestMessages(testResources, testSize = 5000)
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
      testEntryPoint: EntryPoint[IO]
  )
