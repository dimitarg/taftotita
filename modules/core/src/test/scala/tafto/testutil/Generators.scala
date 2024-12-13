package tafto.testutil

import _root_.cats.data.NonEmptyList
import cats.effect.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.Positive
import org.scalacheck.Gen
import tafto.domain.{Email, EmailMessage}
import tafto.util.NonEmpty

object Generators:

  def nonEmptyAsciiString(maxSize: Int :| Positive): Gen[String :| NonEmpty] = for
    size <- Gen.choose(1, maxSize)
    string <- Gen.stringOfN(size, Gen.asciiPrintableChar)
    result <- Gen.const[String :| NonEmpty](string.assume[NonEmpty])
  yield result

  val emailGen: Gen[Email] =
    nonEmptyAsciiString(500)
      .map(Email.apply)

  val emailMessageGen: Gen[EmailMessage] = for
    subject <- Gen.option(nonEmptyAsciiString(500))
    to <- Gen.listOfN(20, emailGen)
    cc <- Gen.listOfN(20, emailGen)
    bcc <- Gen.listOfN(20, emailGen)
    body <- Gen.option(nonEmptyAsciiString(10000))
  yield EmailMessage(
    subject = subject,
    to = to,
    cc = cc,
    bcc = bcc,
    body = body
  )

  def nelOfSize[A](n: Int :| Positive)(gen: Gen[A]): Gen[NonEmptyList[A]] =
    Gen.listOfN(n, gen).flatMap { xs =>
      NonEmptyList
        .fromList(xs)
        .fold {
          // unreachable
          Gen.fail
        } { nel =>
          Gen.const(nel)
        }
    }

  extension [A](gen: Gen[A])
    def sampleF[F[_]: Sync]: F[A] = Sync[F].blocking(gen.sample.get)
    def sampleIO: IO[A] = sampleF[IO]
