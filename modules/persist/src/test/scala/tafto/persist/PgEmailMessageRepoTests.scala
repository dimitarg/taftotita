package tafto.persist

import cats.data.NonEmptyList
import cats.effect.*
import cats.implicits.*
import fs2.Stream
import io.github.iltotore.iron.autoRefine
import io.github.iltotore.iron.cats.given
import natchez.Trace
import tafto.domain.*
import tafto.persist.testutil.{ChannelCodecs as codecs, ChannelIdGenerator}
import tafto.service.comms.EmailMessageRepo
import tafto.testutil.Generators.*
import tafto.util.*
import weaver.pure.*

object PgEmailMessageRepoTests:

  def tests(db: Database[IO], channelGen: ChannelIdGenerator[IO])(using
      trace: Trace[IO]
  ): Stream[IO, Test] =
    val channelCodec = codecs.simple
    parSuite(
      List(
        test("PgEmailMessageRepo.scheduleMessages can insert a single message") {
          for
            msg <- emailMessageGen.sampleIO
            channelId <- channelGen.next
            messageRepo = PgEmailMessageRepo(db, channelId, channelCodec)
            id <- insertMessage(messageRepo)(msg)
            messageFromDb <- messageRepo.getMessage(id)
          yield expect(messageFromDb === (msg, EmailStatus.Scheduled).some)
        },
        test("PgEmailMessageRepo.scheduleMessages can insert a large batch of messages") {
          for
            messages <- nelOfSize(10000)(emailMessageGen).sampleIO
            channelId <- channelGen.next
            messageRepo = PgEmailMessageRepo(db, channelId, channelCodec)
            ids <- messageRepo.scheduleMessages(messages)
          yield expect(ids.size === messages.size)
        },
        test("PgEmailMessageRepo.scheduleMessages notifies on inserting messages") {

          for
            channelId <- channelGen.next
            messageRepo = PgEmailMessageRepo(db, channelId, channelCodec)
            messages <- nelOfSize(5)(emailMessageGen).sampleIO
            firstIdChunk = messageRepo.listen.take(1).compile.lastOrError.background
            result <- firstIdChunk.use { handle =>
              for
                ids <- messageRepo.scheduleMessages(messages)
                chunk <- handle.flatMap(_.embedError)
                broadcastIds = chunk.payload
              yield expect(broadcastIds.toList.toSet === ids.toSet)
            }
          yield result
        },
        test("PgEmailMessageRepo.claim can claim a message") {
          for
            channelId <- channelGen.next
            messageRepo = PgEmailMessageRepo(db, channelId, channelCodec)
            testMessage <- emailMessageGen.sampleIO
            id <- insertMessage(messageRepo)(testMessage)
            (claimedIds, claimedMessages) <- messageRepo
              .claim(List(id))
              .map(_.separate)
            (messageFromDb, status) <- getMessage(messageRepo)(id)
          yield expect(claimedMessages === List(testMessage)) `and`
            expect(claimedMessages === List(messageFromDb)) `and`
            expect(status === EmailStatus.Claimed)
        },
        test("PgEmailMessageRepo.claim can claim a list of messages") {
          for
            channelId <- channelGen.next
            messageRepo = PgEmailMessageRepo(db, channelId, channelCodec)
            testMessages <- nelOfSize(10)(emailMessageGen).sampleIO
            ids <- messageRepo.scheduleMessages(testMessages)
            (claimedIds, claimedMessages) <- messageRepo
              .claim(ids)
              .map(_.separate)
            (messagesFromDb, statuses) <- ids
              .traverse { id =>
                getMessage(messageRepo)(id)
              }
              .map(_.separate)
          yield expect(claimedMessages === testMessages.toList) `and`
            expect(claimedMessages === messagesFromDb.toList) `and`
            expect(statuses.toSet === Set(EmailStatus.Claimed))
        },
        test("PgEmailMessageRepo.claim cannot claim a message once it's claimed") {
          for
            channelId <- channelGen.next
            messageRepo = PgEmailMessageRepo(db, channelId, channelCodec)
            testMessage <- emailMessageGen.sampleIO
            id <- insertMessage(messageRepo)(testMessage)
            _ <- messageRepo.claim(List(id))
            claimedTwice <- messageRepo.claim(List(id))
            (messageFromDb, status) <- getMessage(messageRepo)(id)
          yield expect(claimedTwice === List.empty) `and`
            expect(messageFromDb === testMessage) `and`
            expect(status === EmailStatus.Claimed)
        },
        test("PgEmailMessageRepo.markAsSent can mark a message that was claimed") {
          for
            channelId <- channelGen.next
            messageRepo = PgEmailMessageRepo(db, channelId, channelCodec)
            testMessage <- emailMessageGen.sampleIO
            id <- insertMessage(messageRepo)(testMessage)
            _ <- messageRepo.claim(List(id))
            marked <- messageRepo.markAsSent(id)
            (_, status) <- getMessage(messageRepo)(id)
          yield expect(marked === true) `and` expect(status === EmailStatus.Sent)
        },
        test("PgEmailMessageRepo.markAsSent cannot mark a message that is only scheduled") {
          for
            channelId <- channelGen.next
            messageRepo = PgEmailMessageRepo(db, channelId, channelCodec)
            testMessage <- emailMessageGen.sampleIO
            id <- insertMessage(messageRepo)(testMessage)
            marked <- messageRepo.markAsSent(id)
            (_, status) <- getMessage(messageRepo)(id)
          yield expect(marked === false) `and` expect(status === EmailStatus.Scheduled)
        },
        test("PgEmailMessageRepo.getScheduledIds works correctly") {
          for
            channelId <- channelGen.next
            messageRepo = PgEmailMessageRepo(db, channelId, channelCodec)
            msgs <- nelOfSize(10)(emailMessageGen).sampleIO
            insertedIds <- messageRepo.scheduleMessages(msgs)
            now <- Time[IO].utc
            scheduledIds <- messageRepo.getScheduledIds(now)
          yield expect(insertedIds.toSet.subsetOf(scheduledIds.toSet))
        }
      )
    )

  def insertMessage(repo: EmailMessageRepo[IO])(message: EmailMessage): IO[EmailMessage.Id] = for
    ids <- repo.scheduleMessages(NonEmptyList.one(message))
    id <- ids match
      case List(x) => x.pure[IO]
      case xs      => failure(s"expected single message, got $xs").failFast[IO] >> IO.never
  yield id

  def getMessage(repo: EmailMessageRepo[IO])(id: EmailMessage.Id): IO[(EmailMessage, EmailStatus)] = for
    maybreResult <- repo.getMessage(id)
    result <- maybreResult match
      case None                        => failure("Expected message but none returned.").failFast[IO] >> IO.never
      case Some(messageFromDb, status) => (messageFromDb, status).pure[IO]
  yield result
