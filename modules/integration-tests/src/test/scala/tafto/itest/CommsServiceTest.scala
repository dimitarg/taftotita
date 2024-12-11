package tafto.itest

import java.time.*
import scala.concurrent.duration.*

import cats.implicits.*
import cats.effect.*
import tafto.persist.*
import fs2.*
import weaver.pure.*
import tafto.service.comms.{CommsService, PollingConfig}
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
import monocle.syntax.all.*

object CommsServiceTest:

  def tests(db: Database[IO])(using
      logger: Logger[IO]
  ): Stream[IO, Test] =
    seqSuite(
      List(
        test("Scheduling an email persists and eventually sends email") {
          for
            chanId <- ChannelId("baseline_test").asIO
            emailSender <- RefBackedEmailSender.make[IO]

            emailMessageRepo = PgEmailMessageRepo(db, chanId)
            commsService = CommsService(emailMessageRepo, emailSender, PollingConfig.default)

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
            commsService = CommsService(emailMessageRepo, emailSender, PollingConfig.default)

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
        test("pollForScheduledMessages publishes scheduled entities to channel") {
          for
            chanId <- ChannelId("poll_for_scheduled_test").asIO
            tempChanId <- ChannelId("poll_for_scheduled_test_tmp").asIO
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
            longAgo = OffsetDateTime.ofInstant(Instant.ofEpochMilli(42), ZoneOffset.UTC)
            _ <- db.transact { s =>
              ids.traverse { id =>
                // make sure scheduled message is sufficiently aged so it gets picked up
                s.execute(TestQueries.updateMessageTimestamps)((longAgo, None, id))
              }
            }
            emailMessageRepo = PgEmailMessageRepo(db, chanId)
            commsService = CommsService(emailMessageRepo, emailSender, PollingConfig.default)

            result <- commsService.run.take(10).compile.drain.background.use { awaitFinished =>
              for
                _ <- commsService.pollForScheduledMessages.take(1).compile.drain
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
        test("pollForClaimedMessages processes claimed messages for which TTL has expired") {
          for
            chanId <- ChannelId("poll_for_claimed_test_tmp").asIO
            emailSender <- RefBackedEmailSender.make[IO]
            msg = EmailMessage(
              subject = Some("Comms - reschedule claimed test"),
              to = List(Email("foo@bar.baz")),
              cc = List(Email("cc@example.com")),
              bcc = List(Email("bcc1@example.com"), Email("bcc2@example.com")),
              body = Some("Hello there")
            )
            msgs = NonEmptyList(msg, List.fill(3)(msg))
            tempEmailMessageRepo = PgEmailMessageRepo(db, chanId)
            ids <- tempEmailMessageRepo.scheduleMessages(msgs)

            (id1, id2, id3, id4) <- safeMatch(ids) { case w :: x :: y :: z :: Nil =>
              (w, x, y, z)
            } { xs =>
              s"expected four elements, got $xs"
            }.asIO

            longAgo = OffsetDateTime.ofInstant(Instant.ofEpochMilli(42), ZoneOffset.UTC)

            _ <- db.transact { s =>
              for
                // id1 has expired ttl and is in status claimed - should be processed
                _ <- s.execute(TestQueries.updateMessageTimestamps)((longAgo, longAgo.some, id1))
                _ <- s.execute(TestQueries.updateMessageStatus)((EmailStatus.Claimed, id1))
                // id2 has expired ttl but not claimed - should not be processed
                _ <- s.execute(TestQueries.updateMessageTimestamps)((longAgo, longAgo.some, id2))
                // id3 is claimed but has ttl not expired yet - should not be processed
                _ <- s.execute(TestQueries.updateMessageStatus)((EmailStatus.Claimed, id3))
              // id4 is neither claimed nor has ttl expired - should not be processed
              yield ()
            }

            emailMessageRepo = PgEmailMessageRepo(db, chanId)
            pollingConfig = PollingConfig.default
              .focus(_.forClaimed.timeToLive)
              .replace(1.hour)
            commsService = CommsService(emailMessageRepo, emailSender, pollingConfig)

            _ <- commsService.pollForClaimedMessages.take(1).compile.drain
            (sentIds, _) <- emailSender.getEmails.map(_.separate)
            maybeMessageFromDb <- emailMessageRepo.getMessage(id1)
            messageStatus <- safeMatch(maybeMessageFromDb) { case Some((_, status)) =>
              status
            } { x =>
              s"Expected Some, got $x"
            }.asIO
          yield expect(messageStatus === EmailStatus.Sent) `and` expect(sentIds.toSet === Set(id1))

        },
        test("backfillAndRun makes backfill visible to run()") {
          for
            chanId <- ChannelId("backfill_and_run_test").asIO
            tempChanId <- ChannelId("backfill_and_run_test_tmp").asIO
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
            commsService = CommsService(emailMessageRepo, emailSender, PollingConfig.default)

            result <- commsService.backfillAndRun.compile.drain.background.use { consumerHandle =>
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
            commsService = CommsService(
              emailMessageRepo,
              EmailSender.retrying(retryPolicy)(emailSender),
              PollingConfig.default
            )

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
    def make[F[_]: Sync: Logger](timesToFail: Int :| Positive): F[FlakyEmailSender[F, RefBackedEmailSender[F]]] = for
      underlying <- RefBackedEmailSender.make[F]
      count <- Ref.of(0)
    yield FlakyEmailSender(
      underlying = underlying,
      timesToFail = timesToFail,
      count = count
    )
  }
