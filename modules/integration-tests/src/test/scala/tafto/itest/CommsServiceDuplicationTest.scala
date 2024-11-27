package tafto.itest

import cats.implicits.*
import scala.concurrent.duration.*
import cats.effect.*
import cats.data.NonEmptyList
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.Positive
import tafto.persist.*
import fs2.*
import weaver.pure.*
import tafto.service.comms.CommsService
import tafto.util.*
import tafto.domain.*
import tafto.itest.util.*

import _root_.io.odin.Logger

object CommsServiceDuplicationTest:

  final case class TestCase(
      messageSize: Int :| Positive,
      parallelism: Int :| Positive
  )

  val testCases = List(
    TestCase(messageSize = 1000, parallelism = 2),
    TestCase(messageSize = 1000, parallelism = 4),
    TestCase(messageSize = 1000, parallelism = 8)
  )

  def tests(db: Database[IO])(using
      logger: Logger[IO]
  ): Stream[IO, Test] =
    seqSuite(
      testCases.map { testCase =>
        test(
          s"CommsService consumer prevents duplicate message delivery (message size = ${testCase.messageSize}, parallelism = ${testCase.parallelism})"
        ) {

          for
            chanId <- ChannelId("comms_dedupe_test").asIO
            emailSender <- RefBackedEmailSender.make[IO]

            emailMessageRepo = PgEmailMessageRepo(db, chanId)
            commsService = CommsService(emailMessageRepo, emailSender)

            commsServiceConsumerInstances = Stream
              .emits(List.fill(testCase.parallelism)(commsService.run))
              .parJoinUnbounded

            result <- useBackgroundStream(commsServiceConsumerInstances) {
              val msg = EmailMessage(
                subject = Some("Asdf"),
                to = List(Email("foo@bar.baz")),
                cc = List(Email("cc@example.com")),
                bcc = List(Email("bcc1@example.com"), Email("bcc2@example.com")),
                body = Some("Hello there")
              )
              val messages = NonEmptyList(
                msg,
                List.fill(testCase.messageSize - 1)(msg)
              )

              commsService.scheduleEmails(messages).flatMap { ids =>
                for {
                  sentEmails <- emailSender.waitForIdleAndGetEmails(5.seconds)
                } yield expect(sentEmails.size === testCase.messageSize) `and`
                  expect(sentEmails.map { case (id, _) => id }.toSet === ids.toSet)
              }
            }
          yield result
        }
      }
    )
