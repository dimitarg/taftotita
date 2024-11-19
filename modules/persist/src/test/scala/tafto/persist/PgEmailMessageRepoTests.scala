package tafto.persist

import cats.implicits.*
import cats.effect.*
import cats.data.NonEmptyList
import weaver.pure.*
import fs2.Stream
import tafto.domain.*
import tafto.util.*
import skunk.data.Identifier
import io.github.iltotore.iron.*

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
                yield expect(receivedIds.size === testSize) and
                  expect(receivedIds.toSet === idSet)
              }
            }
          )
        )
      }

  }
}
