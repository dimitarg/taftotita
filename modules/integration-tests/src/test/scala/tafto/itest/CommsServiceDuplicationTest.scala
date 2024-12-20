package tafto.itest

import cats.effect.*
import cats.implicits.*
import cats.mtl.Local
import fs2.{io as _, *}
import io.github.iltotore.iron.constraint.numeric.Positive
import io.github.iltotore.iron.{cats as _, *}
import io.odin.Logger
import natchez.{EntryPoint, Span, Trace}
import tafto.domain.*
import tafto.itest.util.*
import tafto.json.JsonStringCodecs.traceableMessageIdsStringCodec as channelCodec
import tafto.persist.*
import tafto.persist.testutil.ChannelIdGenerator
import tafto.service.comms.CommsService
import tafto.service.comms.CommsService.PollingConfig
import tafto.testutil.Generators.*
import tafto.testutil.tracing.noOpSpanLocal
import tafto.util.*
import weaver.pure.*

import scala.concurrent.duration.*

object CommsServiceDuplicationTest:

  given noOpLocal: Local[IO, Span[IO]] = noOpSpanLocal

  final case class TestCase(
      messageSize: Int :| Positive,
      parallelism: Int :| Positive
  )

  val testCases = List(
    TestCase(messageSize = 1000, parallelism = 2),
    TestCase(messageSize = 1000, parallelism = 4),
    TestCase(messageSize = 1000, parallelism = 8)
  )

  def tests(db: Database[IO], channelGen: ChannelIdGenerator[IO])(using
      logger: Logger[IO],
      trace: Trace[IO],
      entryPoint: EntryPoint[IO]
  ): Stream[IO, Test] =
    seqSuite(
      testCases.map { testCase =>
        test(
          s"CommsService consumer prevents duplicate message delivery (message size = ${testCase.messageSize}, parallelism = ${testCase.parallelism})"
        ) {

          for
            chanId <- channelGen.next
            emailSender <- RefBackedEmailSender.make[IO]

            emailMessageRepo = PgEmailMessageRepo(db, chanId, channelCodec)
            commsService = CommsService(emailMessageRepo, emailSender, PollingConfig.default)

            commsServiceConsumerInstances = Stream
              .emits(List.fill(testCase.parallelism)(commsService.run))
              .parJoinUnbounded

            result <- useBackgroundStream(commsServiceConsumerInstances) {
              for
                messages <- nelOfSize(testCase.messageSize)(emailMessageGen).sampleIO
                ids <- commsService.scheduleEmails(messages)
                sentEmails <- emailSender.waitForIdleAndGetEmails(5.seconds)
              yield expect(sentEmails.size === testCase.messageSize) `and`
                expect(sentEmails.map { (id, _) => id }.toSet === ids.toSet)
            }
          yield result
        }
      }
    )
