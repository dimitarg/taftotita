package tafto.testutil

import _root_.cats.data.NonEmptyList
import cats.effect.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.Positive
import org.scalacheck.Gen
import tafto.domain.{Email, EmailMessage}
import tafto.util.{NonEmpty, TraceableMessage}

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
    Gen.listOfN(n, gen).flatMap(nelOrFail)

  def nelOrFail[A](xs: List[A]): Gen[NonEmptyList[A]] =
    NonEmptyList.fromList(xs).fold(Gen.fail)(Gen.const)

  val emailIdGen: Gen[EmailMessage.Id] =
    Gen.long.map(x => EmailMessage.Id.apply(x))

  private val traceparentHeader = "traceparent"

  // FIXME make this a real gen
  val kernelGen: Gen[Map[String, String]] =
    Gen.const(
      Map(
        traceparentHeader -> "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
      )
    )

  def traceableMessageGen[A](payloadGen: Gen[A]): Gen[TraceableMessage[A]] = for
    kernel <- kernelGen
    payload <- payloadGen
  yield TraceableMessage(kernel, payload)

  extension [A](gen: Gen[A])
    def sampleF[F[_]: Sync]: F[A] = Sync[F].blocking(gen.sample.get)
    def sampleIO: IO[A] = sampleF[IO]
