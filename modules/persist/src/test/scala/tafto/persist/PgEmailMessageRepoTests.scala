package tafto.persist

import cats.implicits.*
import cats.effect.*
import cats.data.NonEmptyList
import weaver.pure.*
import fs2.Stream
import tafto.domain.*
import tafto.util.*
import io.github.iltotore.iron.autoRefine
import io.github.iltotore.iron.cats.given
import tafto.service.comms.EmailMessageRepo

object PgEmailMessageRepoTests {

  def tests(db: Database[IO]): Stream[IO, Test] = {
    Stream
      .eval(
        ChannelId("pgemailmessagerepotests").asIO
      )
      .flatMap { channelId =>
        val messageRepo = PgEmailMessageRepo(db, channelId)
        parSuite(
          List(
            test("PgEmailMessageRepo.scheduleMessages can insert a single message") {
              val msg = EmailMessage(
                subject = Some("Insert Single"),
                to = List(Email("foo@bar.baz")),
                cc = List(Email("cc@example.com")),
                bcc = List(Email("bcc1@example.com"), Email("bcc2@example.com")),
                body = Some("Hello there")
              )
              for
                id <- insertMessage(messageRepo)(msg)
                messageFromDb <- messageRepo.getMessage(id)
              yield expect(messageFromDb === (msg, EmailStatus.Scheduled).some)
            },
            test("PgEmailMessageRepo.scheduleMessages can insert a large batch of messages") {
              val testMessage = EmailMessage(
                subject = Some("Insert Multi"),
                to = List(Email("foo@bar.baz")),
                cc = List(Email("cc@example.com")),
                bcc = List(Email("bcc1@example.com"), Email("bcc2@example.com")),
                body = Some("Hello there")
              )

              val messages = NonEmptyList(testMessage, List.fill(10000)(testMessage))
              messageRepo.scheduleMessages(messages).map { ids =>
                expect(ids.size === messages.size)
              }
            },
            test("PgEmailMessageRepo.scheduleMessages notifies on inserting messages") {
              val testMessage = EmailMessage(
                subject = Some("Insert Notifications"),
                to = List(Email("foo@bar.baz")),
                cc = List(Email("cc@example.com")),
                bcc = List(Email("bcc1@example.com"), Email("bcc2@example.com")),
                body = Some("Hello there")
              )

              val testSize = 5
              val messages = NonEmptyList(testMessage, List.fill(testSize - 1)(testMessage))

              TestChannelListener.make(db, channelId).use { messageStream =>
                for
                  ids <- messageRepo.scheduleMessages(messages)
                  idSet = ids.map(_.value.show).toSet
                  receivedIds <- messageStream
                    .filter(idSet.contains)
                    .take(testSize)
                    .compile
                    .toList
                yield expect(receivedIds.size === testSize) `and`
                  expect(receivedIds.toSet === idSet)
              }
            },
            test("PgEmailMessageRepo.claim can claim a message") {
              val testMessage = EmailMessage(
                subject = Some("Claim"),
                to = List(Email("a@bar.baz")),
                cc = List(Email("b@example.com")),
                bcc = List(Email("c@example.com"), Email("d@example.com")),
                body = Some("Yoyo.")
              )

              for
                id <- insertMessage(messageRepo)(testMessage)
                claimed <- messageRepo.claim(id)
                (messageFromDb, status) <- getMessage(messageRepo)(id)
              yield expect(claimed === testMessage.some) `and`
                expect(claimed === messageFromDb.some) `and`
                expect(status === EmailStatus.Claimed)
            },
            test("PgEmailMessageRepo.claim cannot claim a message once it's claimed") {
              val testMessage = EmailMessage(
                subject = Some("Claim twice"),
                to = List(Email("a@bar.baz")),
                cc = List(Email("b@example.com")),
                bcc = List(Email("c@example.com"), Email("d@example.com")),
                body = Some("Yoyoyo.")
              )

              for
                id <- insertMessage(messageRepo)(testMessage)
                _ <- messageRepo.claim(id)
                claimedTwice <- messageRepo.claim(id)
                (messageFromDb, status) <- getMessage(messageRepo)(id)
              yield expect(claimedTwice === None) `and`
                expect(messageFromDb === testMessage) `and`
                expect(status === EmailStatus.Claimed)
            },
            test("PgEmailMessageRepo.markAsSent can mark a message that was claimed") {
              val testMessage = EmailMessage(
                subject = Some("Mark as sent"),
                to = List(Email("a@bar.baz")),
                cc = List(Email("b@example.com")),
                bcc = List(Email("c@example.com"), Email("d@example.com")),
                body = Some("Yoyo.")
              )

              for
                id <- insertMessage(messageRepo)(testMessage)
                _ <- messageRepo.claim(id)
                marked <- messageRepo.markAsSent(id)
                (_, status) <- getMessage(messageRepo)(id)
              yield expect(marked === true) `and` expect(status === EmailStatus.Sent)
            },
            test("PgEmailMessageRepo.markAsSent cannot mark a message that is only scheduled") {
              val testMessage = EmailMessage(
                subject = Some("Mark scheduled as sent"),
                to = List(Email("a@bar.baz")),
                cc = List(Email("b@example.com")),
                bcc = List(Email("c@example.com"), Email("d@example.com")),
                body = Some("Yoyo.")
              )

              for
                id <- insertMessage(messageRepo)(testMessage)
                marked <- messageRepo.markAsSent(id)
                (_, status) <- getMessage(messageRepo)(id)
              yield expect(marked === false) `and` expect(status === EmailStatus.Scheduled)
            },
            test("PgEmailMessageRepo.getScheduledIds works correctly") {
              val msg = EmailMessage(
                subject = Some("Scheduled ids"),
                to = List(Email("a@bar.baz")),
                cc = List(Email("b@example.com")),
                bcc = List(Email("c@example.com"), Email("d@example.com")),
                body = Some("Yoyo.")
              )
              val msgs = NonEmptyList(msg, List.fill(9)(msg))

              for
                insertedIds <- messageRepo.scheduleMessages(msgs)
                now <- Time[IO].utc
                scheduledIds <- messageRepo.getScheduledIds(now)
              yield expect(insertedIds.toSet.subsetOf(scheduledIds.toSet))
            }
          )
        )
      }

  }

  def insertMessage(repo: EmailMessageRepo[IO])(message: EmailMessage): IO[EmailMessage.Id] = for
    ids <- repo.scheduleMessages(NonEmptyList.one(message))
    id <- ids match {
      case List(x) => x.pure[IO]
      case xs      => failure(s"expected single message, got $xs").failFast[IO] >> IO.never
    }
  yield id

  def getMessage(repo: EmailMessageRepo[IO])(id: EmailMessage.Id): IO[(EmailMessage, EmailStatus)] = for
    maybreResult <- repo.getMessage(id)
    result <- maybreResult match
      case None                        => failure("Expected message but none returned.").failFast[IO] >> IO.never
      case Some(messageFromDb, status) => (messageFromDb, status).pure[IO]
  yield result
}
