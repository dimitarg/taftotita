package tafto.service.comms

import cats.effect.IO
import cats.implicits.*
import fs2.Stream
import io.odin.Logger
import tafto.log.defaultLogger
import tafto.testutil.*
import weaver.pure.*

import scala.concurrent.duration.*

object CancellationExploratoryTest extends Suite:

  given logger: Logger[IO] = defaultLogger

  def attempt[A](x: IO[A]): IO[Either[String, A]] = x.attempt.map(_.leftMap(_.getMessage))

  override def suitesStream: Stream[IO, Test] = parSuite(
    List(
      test("`uncancelable` does not mask inner cancellation") {
        val timeout = 5.millis
        val misbehaving: IO[Unit] = IO.never
        val withTimeout = misbehaving.timeout(timeout)
        val unancelable = (withTimeout).uncancelable

        attempt(unancelable).map { result =>
          expect(result === timeout.show.asLeft)
        }
      }.logDuration,
      test("`uncancelable` does not mask inner cancellation - example 2") {
        val timeout = 5.millis
        val ok = IO.unit
        val misbehaving: IO[Unit] = IO.never
        val withTimeout = misbehaving.timeout(timeout)
        val unancelable = (ok >> withTimeout).uncancelable

        attempt(unancelable).map { result =>
          expect(result === timeout.show.asLeft)
        }
      }.logDuration
    )
  )
