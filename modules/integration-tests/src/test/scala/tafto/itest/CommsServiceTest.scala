package tafto.itest

import cats.data.NonEmptyList
import cats.effect.*
import cats.implicits.*
import cats.mtl.Local
import fs2.{io as _, *}
import io.github.iltotore.iron.cats.given
import io.github.iltotore.iron.constraint.numeric.*
import io.github.iltotore.iron.{cats as _, *}
import io.odin.Logger
import monocle.syntax.all.*
import natchez.{EntryPoint, Span, Trace}
import tafto.domain.*
import tafto.itest.util.*
import tafto.json.JsonStringCodecs.traceableMessageIdsStringCodec as channelCodec
import tafto.persist.*
import tafto.persist.testutil.ChannelIdGenerator
import tafto.service.comms.CommsService.PollingConfig
import tafto.service.comms.{CommsService, EmailSender}
import tafto.service.util.Retry
import tafto.testutil.Generators.*
import tafto.testutil.tracing.*
import tafto.util.*
import tafto.util.tracing.TraceRoot
import weaver.pure.*

import java.time.*
import scala.concurrent.duration.*

object CommsServiceTest:

  def tests(db: Database[IO], channelGen: ChannelIdGenerator[IO])(using
      logger: Logger[IO],
      trace: Trace[IO],
      entryPoint: EntryPoint[IO]
  ): Stream[IO, Test] =
    given noOpLocal: Local[IO, Span[IO]] = noOpSpanLocal
    given traceRoot: TraceRoot[IO] = TraceRoot.make[IO]
    seqSuite(
      List(
        test("Scheduling an email persists and eventually sends email") {
          for
            chanId <- channelGen.next
            emailSender <- RefBackedEmailSender.make[IO]

            emailMessageRepo = PgEmailMessageRepo(db, chanId, channelCodec)
            commsService = CommsService(emailMessageRepo, emailSender, PollingConfig.default)

            result <- commsService.run.take(1).compile.drain.background.use { awaitFinished =>
              for
                msg <- emailMessageGen.sampleIO
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
          val emailSender: EmailSender[IO] =
            (_: EmailMessage.Id, _: EmailMessage) => IO.raiseError(new RuntimeException("Failed I have."))
          for
            chanId <- channelGen.next
            emailMessageRepo = PgEmailMessageRepo(db, chanId, channelCodec)
            commsService = CommsService(emailMessageRepo, emailSender, PollingConfig.default)

            result <- commsService.run.take(1).compile.drain.background.use { awaitFinished =>
              for
                msg <- emailMessageGen.sampleIO
                scheduledIds <- commsService.scheduleEmails(NonEmptyList.one(msg))
                _ <- awaitFinished.flatMap(_.embedError)
                dbEmails <- scheduledIds.traverse(emailMessageRepo.getMessage).map(_.flatten)
              yield expect(dbEmails === List((msg, EmailStatus.Error)))
            }
          yield result
        },
        test("pollForScheduledMessages publishes scheduled entities to channel") {
          for
            chanId <- channelGen.next
            tempChanId <- channelGen.next
            emailSender <- RefBackedEmailSender.make[IO]
            msgs <- nelOfSize(10)(emailMessageGen).sampleIO
            tempEmailMessageRepo = PgEmailMessageRepo(db, tempChanId, channelCodec)
            ids <- tempEmailMessageRepo.scheduleMessages(msgs)
            longAgo = OffsetDateTime.ofInstant(Instant.ofEpochMilli(42), ZoneOffset.UTC)
            _ <- db.transact { s =>
              ids.traverse { id =>
                // make sure scheduled message is sufficiently aged so it gets picked up
                s.execute(TestQueries.updateMessageTimestamps)((longAgo, None, id))
              }
            }
            emailMessageRepo = PgEmailMessageRepo(db, chanId, channelCodec)
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
            chanId <- channelGen.next
            emailSender <- RefBackedEmailSender.make[IO]
            msgs <- nelOfSize(4)(emailMessageGen).sampleIO
            tempEmailMessageRepo = PgEmailMessageRepo(db, chanId, channelCodec)
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

            emailMessageRepo = PgEmailMessageRepo(db, chanId, channelCodec)
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
            chanId <- channelGen.next
            tempChanId <- channelGen.next
            emailSender <- RefBackedEmailSender.make[IO]
            msgs <- nelOfSize(10)(emailMessageGen).sampleIO
            tempEmailMessageRepo = PgEmailMessageRepo(db, tempChanId, channelCodec)
            backfillIds <- tempEmailMessageRepo.scheduleMessages(msgs)

            emailMessageRepo = PgEmailMessageRepo(db, chanId, channelCodec)
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
            chanId <- channelGen.next
            emailSender <- FlakyEmailSender.make[IO](timesToFail = 2)
            emailMessageRepo = PgEmailMessageRepo(db, chanId, channelCodec)
            retryPolicy = Retry.fullJitter[IO](maxRetries = 3, baseDelay = 2.millis)
            commsService = CommsService(
              emailMessageRepo,
              EmailSender.retrying(retryPolicy)(emailSender),
              PollingConfig.default
            )
            result <- commsService.run.take(1).compile.drain.background.use { awaitFinished =>
              for
                msg <- emailMessageGen.sampleIO
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
