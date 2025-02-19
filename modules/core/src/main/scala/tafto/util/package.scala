package tafto

import _root_.cats.data.NonEmptyList
import _root_.cats.effect.IO
import cats.MonadThrow
import cats.implicits.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

package object util:
  type NonEmpty = MinLength[1]
  type SizedBetween[Min <: Int, Max <: Int] = MinLength[Min] & MaxLength[Max]
  type NonEmptyString = String :| NonEmpty
  object NonEmptyString extends RefinedTypeOps.Transparent[NonEmptyString]

  opaque type Host = NonEmptyString
  object Host extends RefinedTypeOps[String, NonEmpty, Host]

  type ValidPortRange = GreaterEqual[0] & LessEqual[65535]
  opaque type Port = Int :| ValidPortRange
  object Port extends RefinedTypeOps[Int, ValidPortRange, Port]

  def safeAssert[F[_]: MonadThrow](cond: Boolean, error: => String): F[Unit] =
    if cond then ().pure else MonadThrow[F].raiseError(new RuntimeException(error))

  extension [A](either: Either[String, A])
    def errorAsThrowable: Either[Throwable, A] = either.leftMap(x => new RuntimeException(x)).leftWiden[Throwable]
    def orThrow[F[_]: MonadThrow]: F[A] = MonadThrow[F].fromEither(errorAsThrowable)
    def asIO: IO[A] = orThrow[IO]

  extension [A](xs: List[A])
    def toNel(error: => String): Either[String, NonEmptyList[A]] = NonEmptyList
      .fromList(xs)
      .toRight(error)

  def safeMatch[A, B](x: A)(f: PartialFunction[A, B])(error: A => String): Either[String, B] =
    if f.isDefinedAt(x) then f(x).asRight
    else error(x).asLeft
