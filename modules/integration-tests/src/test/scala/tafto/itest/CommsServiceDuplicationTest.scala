package tafto.itest

import scala.concurrent.duration.*
import io.github.iltotore.iron.autoRefine
import tafto.persist.{Database, PgEmailMessageRepo}
import io.odin.*
import fs2.*
import cats.effect.*
import weaver.pure.*
import tafto.itest.util.RefBackedEmailSender
import tafto.service.comms.CommsService
import tafto.util.*
import tafto.domain.*
import skunk.data.Identifier
import cats.data.NonEmptyList

object CommsServiceDuplicationTest:
  def tests(db: Database[IO]): Stream[IO, Test] =
    Stream
      .eval(Identifier.fromString("commsdedupetest".toLowerCase()).asIO) // FIXME toLowerCase
      .flatMap { chanId =>
        Stream.eval(RefBackedEmailSender.make[IO]).flatMap { emailSender =>
          given logger: Logger[IO] = consoleLogger[IO]()
          val emailMessageRepo = PgEmailMessageRepo(db, chanId)
          val commsService = CommsService(emailMessageRepo, emailSender)

          val commsServiceConsumerInstances = Stream.emits(List.fill(4)(commsService.run)).parJoinUnbounded

          commsServiceConsumerInstances.spawn[IO].flatMap { _ =>
            parSuite(
              List(
                test("here goes") {
                  val msg = EmailMessage(
                    subject = Some("Asdf"),
                    to = List(Email("foo@bar.baz")),
                    cc = List(Email("cc@example.com")),
                    bcc = List(Email("bcc1@example.com"), Email("bcc2@example.com")),
                    body = Some("Hello there")
                  )
                  val messages = NonEmptyList(
                    msg,
                    List.fill(9999)(msg)
                  )

                  IO.sleep(5000.millis) >> commsService.scheduleEmails(messages).flatMap { ids =>
                    for {
                      _ <- IO.println(s"scheduled ${ids.size} messages for delivery.")
                      _ <- IO.sleep(20.seconds).as(success) // todo cats-retry or such
                      sentEmails <- emailSender.getEmails
                      _ <- IO.println(s"Sent ${sentEmails.size} emails")
                    } yield success
                  }

                }
              )
            )
          }

        }
      }
