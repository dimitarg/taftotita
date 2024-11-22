package tafto.persist

import cats.implicits.*
import cats.effect.*
import cats.data.NonEmptyList
import weaver.pure.*
import fs2.Stream
import tafto.domain.*
import tafto.util.*
import skunk.data.Identifier
import io.github.iltotore.iron.autoRefine
import io.github.iltotore.iron.cats.given
import tafto.service.comms.EmailMessageRepo

object PgEmailMessageRepoTests {

  def tests(db: Database[IO]): Stream[IO, Test] = {
    Stream
      .eval(
        Identifier.fromString("PgEmailMessageRepoTests".toLowerCase()).asIO // FIXME toLowerCase
      )
      .flatMap { channelId =>
        val messageRepo = PgEmailMessageRepo(db, channelId)
        parSuite(
          List(
            test("PgEmailMessageRepo.insertMessages can insert a single message") {
              messageRepo
                .insertMessages(
                  NonEmptyList.one(
                    EmailMessage(
                      subject = Some("Asdf"),
                      to = List(Email("foo@bar.baz")),
                      cc = List(Email("cc@example.com")),
                      bcc = List(Email("bcc1@example.com"), Email("bcc2@example.com")),
                      body = Some("Hello there")
                    )
                  )
                )
                .map { ids =>
                  expect(ids.size === 1)
                }
            },
            test("PgEmailMessageRepo.insertMessages can insert a large batch of messages") {
              val testMessage = EmailMessage(
                subject = Some("Asdf"),
                to = List(Email("foo@bar.baz")),
                cc = List(Email("cc@example.com")),
                bcc = List(Email("bcc1@example.com"), Email("bcc2@example.com")),
                body = Some("Hello there")
              )

              val messages = NonEmptyList(testMessage, List.fill(10000)(testMessage))
              messageRepo.insertMessages(messages).map { ids =>
                expect(ids.size === messages.size)
              }
            },
            test("PgEmailMessageRepo.insertMessages notifies on inserting messages") {
              val testMessage = EmailMessage(
                subject = Some("Asdf"),
                to = List(Email("foo@bar.baz")),
                cc = List(Email("cc@example.com")),
                bcc = List(Email("bcc1@example.com"), Email("bcc2@example.com")),
                body = Some("Hello there")
              )

              val testSize = 5
              val messages = NonEmptyList(testMessage, List.fill(testSize - 1)(testMessage))

              TestChannelListener.make(db, channelId).use { messageStream =>
                for
                  ids <- messageRepo.insertMessages(messages)
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
            test("PgEmailMessageRepo.getMessage works correctly") {
              val testMessage = EmailMessage(
                subject = Some("Woohoo"),
                to = List(Email("ok@bar.baz")),
                cc = List(Email("one@example.com")),
                bcc = List(Email("two@example.com"), Email("three@example.com")),
                body = Some("Yo.")
              )

              for {
                id <- insertMessage(messageRepo)(testMessage)
                (messageFromDb, status) <- getMessage(messageRepo)(id)
              } yield expect(messageFromDb === testMessage) `and` expect(status === EmailStatus.Scheduled)
            },
            test("PgEmailMessageRepo.markAsSent works correctly") {
              val testMessage = EmailMessage(
                subject = Some("asd"),
                to = List(Email("a@bar.baz")),
                cc = List(Email("b@example.com")),
                bcc = List(Email("c@example.com"), Email("d@example.com")),
                body = Some("Yoyo.")
              )

              for
                id <- insertMessage(messageRepo)(testMessage)
                marked <- messageRepo.markAsSent(id)
                (_, status) <- getMessage(messageRepo)(id)
              yield expect(marked === true) `and` expect(status === EmailStatus.Sent)
            }
          )
        )
      }

  }

  def insertMessage(repo: EmailMessageRepo[IO])(message: EmailMessage): IO[EmailMessage.Id] = for
    ids <- repo.insertMessages(NonEmptyList.one(message))
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
