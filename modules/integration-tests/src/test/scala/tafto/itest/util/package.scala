package tafto.itest

import cats.effect.*
import cats.implicits.*
import fs2.Stream

import scala.concurrent.duration.*

package object util:
  def repeatWhile[F[_]: Temporal, A](prg: F[A])(pred: (Option[A], A) => Boolean)(interval: FiniteDuration): F[A] =
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

  def useBackgroundStream[F[_]: Concurrent, A, B](xs: Stream[F, A])(prg: F[B]): F[B] =
    xs.spawn.flatMap(_ => Stream.eval(prg)).compile.lastOrError
