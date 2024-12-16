package tafto.testutil

import cats.Applicative
import cats.effect.IO
import cats.mtl.Local
import natchez.Span
import natchez.noop.NoopSpan

package object tracing:
  val noOpSpanLocal: Local[IO, Span[IO]] = new Local[IO, Span[IO]]:

    override def applicative: Applicative[IO] = Applicative[IO]

    override def ask[E2 >: Span[IO]]: IO[E2] = IO.pure(NoopSpan())

    override def local[A](fa: IO[A])(f: Span[IO] => Span[IO]): IO[A] = fa
