package tafto.itest

import cats.implicits.*
import scala.concurrent.duration.*
import io.github.iltotore.iron.autoRefine
import tafto.persist.{Database, PgEmailMessageRepo}
import fs2.*
import cats.effect.*
import weaver.pure.*
import tafto.itest.util.RefBackedEmailSender
import tafto.service.comms.CommsService
import tafto.util.*
import tafto.domain.*
import skunk.data.Identifier
import cats.data.NonEmptyList
import tafto.log.given
import _root_.io.odin.Logger

object CommsServiceDuplicationTest:
  def tests(db: Database[IO]): Stream[IO, Test] =
    Stream
      .eval(Identifier.fromString("commsdedupetest".toLowerCase()).asIO) // FIXME toLowerCase
      .flatMap { chanId =>
        Stream.eval(RefBackedEmailSender.make[IO]).flatMap { emailSender =>
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

                  commsService.scheduleEmails(messages).flatMap { ids =>
                    for {
                      _ <- Logger[IO].info(s"scheduled ${ids.size} messages for delivery.")
                      sentEmails <- repeatWhile(emailSender.getEmails) {
                        case (None, _)                 => true
                        case (Some(previous), current) => current.length > previous.length
                      }(5.seconds)
                      _ <- Logger[IO].info(s"Sent ${sentEmails.size} emails")
                    } yield success
                  }

                }
              )
            )
          }

        }
      }

  def repeatWhile[F[_]: Temporal, A](prg: F[A])(pred: (Option[A], A) => Boolean)(interval: FiniteDuration): F[A] = {
    Stream
      .repeatEval(prg)
      .metered(interval)
      .zipWithPrevious
      .takeWhile { case (prev, curr) =>
        pred(prev, curr)
      }
      .compile
      .lastOrError
      .map { case (_, x) => x }
  }
