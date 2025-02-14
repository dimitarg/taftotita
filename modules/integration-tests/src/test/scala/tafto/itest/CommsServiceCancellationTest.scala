package tafto.itest

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.std.Random
import cats.implicits.*
import fs2.Stream
import io.github.iltotore.iron.given
import io.odin.Logger
import org.typelevel.otel4s.trace.Tracer
import tafto.domain.*
import tafto.itest.util.*
import tafto.json.JsonStringCodecs.traceableMessageIdsStringCodec as channelCodec
import tafto.persist.testutil.ChannelIdGenerator
import tafto.persist.{Database, PgEmailMessageRepo}
import tafto.service.comms.CommsService
import tafto.service.comms.CommsService.PollingConfig
import tafto.testutil.*
import tafto.testutil.Generators.*
import weaver.pure.*

import scala.concurrent.duration.*

object CommsServiceCancellationTest:

  val suiteSize = 10
  val emailLatency = 50.millis
  val testLength = 5.seconds

  val message = EmailMessage(
    subject = Some("Hello there"),
    to = List(Email("foo@example.com")),
    cc = List(Email("bar@example.com")),
    bcc = List(Email("bar@example.com")),
    body = Some("General Kenobi!")
  )
  val messages = NonEmptyList(message, List.fill(999999)(message))

  def tests(db: Database[IO], channelGen: ChannelIdGenerator[IO])(using
      logger: Logger[IO],
      tracer: Tracer[IO]
  ): Stream[IO, Test] = seqSuite(
    List.range(1, suiteSize + 1).map { x =>
      test(s"Comms service finishes processing in-flight messages on cancellation (attempt $x)") {
        for
          chanId <- channelGen.next
          emailSender <- RefBackedEmailSender.make[IO]
          random <- Random.scalaUtilRandom[IO]
          slowSender = LatencySimulatingEmailSender(emailSender, random, emailLatency)

          emailMessageRepo = PgEmailMessageRepo(db, chanId, channelCodec)
          commsService = CommsService(emailMessageRepo, slowSender, PollingConfig.default)
          publisherFiber <- messages
            .grouped(500)
            .toList
            .traverse { batch =>
              commsService.scheduleEmails(batch)
            }
            .start
          consumerFiber <- commsService.backfillAndRun.compile.drain.start
          _ <- IO.sleep(testLength)
          _ <- consumerFiber.cancel
          _ <- publisherFiber.cancel
          sentIds <- emailSender.getEmails.map(_.map { case (id, _) => id })
          dbIdsAndStatuses <- getStatuses(db)(sentIds)
          (dbIds, dbStatuses) = dbIdsAndStatuses.separate
          dbStatusSet = dbStatuses.toSet
          _ <- logger.info(s"sent ${sentIds.size} emails")
          _ <- logger.info(s"sent email status set: ${dbStatusSet}")
        yield expect(sentIds.toSet === dbIds.toSet) `and`
          expect(dbStatusSet.contains(EmailStatus.Scheduled) === false) `and`
          expect(dbStatusSet.contains(EmailStatus.Claimed) === false)
      }.logDuration.guarantee(deleteAllMessages(db))
    }
  )

  def getStatuses(db: Database[IO])(ids: List[EmailMessage.Id]): IO[List[(EmailMessage.Id, EmailStatus)]] =
    NonEmptyList
      .fromList(ids)
      .traverse { nelIds =>
        db.transact { session =>
          Database.batched(session)(TestQueries.getMessageStatuses)(nelIds)
        }
      }
      .map(_.getOrElse(List.empty))

  def deleteAllMessages(db: Database[IO]): IO[Unit] =
    db.transact(s => s.execute(TestQueries.deleteAllMessages)).void
