package tafto.itest

import scala.concurrent.duration.*

import cats.implicits.*
import cats.effect.*
import tafto.persist.*
import fs2.*
import weaver.pure.*
import tafto.service.comms.CommsService
import tafto.domain.*
import tafto.util.*
import tafto.itest.util.*

import _root_.io.odin.Logger
import tafto.domain.EmailMessage
import _root_.cats.data.NonEmptyList
import tafto.service.comms.EmailSender
import tafto.domain.EmailMessage.Id
import _root_.io.github.iltotore.iron.cats.given
import _root_.io.github.iltotore.iron.*
import _root_.io.github.iltotore.iron.constraint.numeric.*
import _root_.cats.MonadThrow
import tafto.service.util.Retry

object CommsServiceTest:

  def tests(db: Database[IO])(using
      logger: Logger[IO]
  ): Stream[IO, Test] =
    parSuite(
      List(
        test("Scheduling an email persists and eventually sends email") {
          for
            chanId <- ChannelId("baseline_test").asIO
            emailSender <- RefBackedEmailSender.make[IO]

            emailMessageRepo = PgEmailMessageRepo(db, chanId)
            commsService = CommsService(emailMessageRepo, emailSender)

            msg = EmailMessage(
              subject = Some("Comms baseline test"),
              to = List(Email("foo@bar.baz")),
              cc = List(Email("cc@example.com")),
              bcc = List(Email("bcc1@example.com"), Email("bcc2@example.com")),
              body = Some("Hello there")
            )

            result <- commsService.run.take(1).compile.drain.background.use { awaitFinished =>
              for
                _ <- commsService.scheduleEmails(NonEmptyList.one(msg))
                _ <- awaitFinished.void
                sent <- emailSender.getEmails
                (sentIds, sentEmails) = sent.separate
                dbEmails <- sentIds.traverse(emailMessageRepo.getMessage).map(_.flatten)
              yield expect(sentEmails === List(msg)) `and`
                expect(dbEmails === List((msg, EmailStatus.Sent)))
            }
          yield result
        },
        test("Errors when sending email are persisted") {
          val emailSender: EmailSender[IO] = { (_: Id, _: EmailMessage) =>
            IO.raiseError(new RuntimeException("Failed I have."))
          }
          for
            chanId <- ChannelId("error_test").asIO
            emailMessageRepo = PgEmailMessageRepo(db, chanId)
            commsService = CommsService(emailMessageRepo, emailSender)

            msg = EmailMessage(
              subject = Some("Comms error test"),
              to = List(Email("foo@bar.baz")),
              cc = List(Email("cc@example.com")),
              bcc = List(Email("bcc1@example.com"), Email("bcc2@example.com")),
              body = Some("Hello there")
            )

            result <- commsService.run.take(1).compile.drain.background.use { awaitFinished =>
              for
                scheduledIds <- commsService.scheduleEmails(NonEmptyList.one(msg))
                _ <- awaitFinished.flatMap(_.embedError)
                dbEmails <- scheduledIds.traverse(emailMessageRepo.getMessage).map(_.flatten)
              yield expect(dbEmails === List((msg, EmailStatus.Error)))
            }
          yield result
        },
        test("Backfill publishes scheduled entities to channel") {
          for
            chanId <- ChannelId("backfill_test").asIO
            tempChanId <- ChannelId("backfill_test_tmp").asIO
            emailSender <- RefBackedEmailSender.make[IO]
            msg = EmailMessage(
              subject = Some("Comms error test"),
              to = List(Email("foo@bar.baz")),
              cc = List(Email("cc@example.com")),
              bcc = List(Email("bcc1@example.com"), Email("bcc2@example.com")),
              body = Some("Hello there")
            )
            msgs = NonEmptyList(msg, List.fill(9)(msg))
            tempEmailMessageRepo = PgEmailMessageRepo(db, tempChanId)
            ids <- tempEmailMessageRepo.scheduleMessages(msgs)
            emailMessageRepo = PgEmailMessageRepo(db, chanId)
            commsService = CommsService(emailMessageRepo, emailSender)

            result <- commsService.run.take(10).compile.drain.background.use { awaitFinished =>
              for
                _ <- commsService.backfill
                streamResult <- awaitFinished
                sent <- emailSender.getEmails
                (sentIds, sentEmails) = sent.separate
                dbEmails <- ids.traverse(emailMessageRepo.getMessage).map(_.flatten)
              yield expect(dbEmails === msgs.map { x => (x, EmailStatus.Sent) }.toList) `and`
                expect(sentEmails === msgs.toList) `and`
                expect(sentIds === ids)
            }
          yield success
        },
        test("backfillAndRun makes backfill visible to run()") {
          for
            chanId <- ChannelId("backfill_test").asIO
            tempChanId <- ChannelId("backfill_test_tmp").asIO
            emailSender <- RefBackedEmailSender.make[IO]
            msg = EmailMessage(
              subject = Some("Comms error test"),
              to = List(Email("foo@bar.baz")),
              cc = List(Email("cc@example.com")),
              bcc = List(Email("bcc1@example.com"), Email("bcc2@example.com")),
              body = Some("Hello there")
            )
            msgs = NonEmptyList(msg, List.fill(9)(msg))
            tempEmailMessageRepo = PgEmailMessageRepo(db, tempChanId)
            backfillIds <- tempEmailMessageRepo.scheduleMessages(msgs)

            emailMessageRepo = PgEmailMessageRepo(db, chanId)
            commsService = CommsService(emailMessageRepo, emailSender)

            result <- commsService.backfillAndRun.background.use { consumerHandle =>
              for
                liveIds <- commsService.scheduleEmails(msgs)
                sent <- emailSender.waitForIdleAndGetEmails(5.seconds)
                (sentIds, _) = sent.separate
              yield expect(sentIds.toSet === (backfillIds ++ liveIds).toSet)
            }
          yield success
        },
        test("Email sending is retried in case of an error") {
          for
            chanId <- ChannelId("error_retry_test").asIO
            emailSender <- FlakyEmailSender.make[IO](timesToFail = 2)
            emailMessageRepo = PgEmailMessageRepo(db, chanId)
            retryPolicy = Retry.fullJitter[IO](maxRetries = 3, baseDelay = 2.millis)
            commsService = CommsService(emailMessageRepo, EmailSender.retrying(retryPolicy)(emailSender))

            msg = EmailMessage(
              subject = Some("Comms error retry test"),
              to = List(Email("foo@bar.baz")),
              cc = List(Email("cc@example.com")),
              bcc = List(Email("bcc1@example.com"), Email("bcc2@example.com")),
              body = Some("Hello there")
            )

            result <- commsService.run.take(1).compile.drain.background.use { awaitFinished =>
              for
                scheduledIds <- commsService.scheduleEmails(NonEmptyList.one(msg))
                _ <- awaitFinished.flatMap(_.embedError)
                dbEmails <- scheduledIds.traverse(emailMessageRepo.getMessage).map(_.flatten)
                (sentIds, sentEmails) <- emailSender.underlying.getEmails.map(_.separate)
              yield expect(dbEmails === List((msg, EmailStatus.Sent))) `and`
                expect(sentEmails === List(msg)) `and`
                expect(sentIds === scheduledIds)
            }
          yield result
        }
      )
    )

  final case class FlakyEmailSender[F[_]: MonadThrow, Underlying <: EmailSender[F]] private (
      underlying: Underlying,
      private val count: Ref[F, Int],
      private val timesToFail: Int :| Positive
  ) extends EmailSender[F]:
    override def sendEmail(id: Id, email: EmailMessage): F[Unit] =
      count.updateAndGet(_ + 1).flatMap { cnt =>
        if (cnt > timesToFail) {
          underlying.sendEmail(id, email)
        } else {
          MonadThrow[F].raiseError(new RuntimeException("Flaky email sender."))
        }
      }

  object FlakyEmailSender {
    def make[F[_]: Sync](timesToFail: Int :| Positive): F[FlakyEmailSender[F, RefBackedEmailSender[F]]] = for
      underlying <- RefBackedEmailSender.make[F]
      count <- Ref.of(0)
    yield FlakyEmailSender(
      underlying = underlying,
      timesToFail = timesToFail,
      count = count
    )
  }
