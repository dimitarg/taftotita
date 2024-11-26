package tafto.itest

import cats.implicits.*
import cats.effect.*
import io.github.iltotore.iron.*
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
                streamResult <- awaitFinished
                dbEmails <- scheduledIds.traverse(emailMessageRepo.getMessage).map(_.flatten)
              yield expect(dbEmails === List((msg, EmailStatus.Error))) `and`
                expect(streamResult.isSuccess)
            }
          yield result
        }
      )
    )
