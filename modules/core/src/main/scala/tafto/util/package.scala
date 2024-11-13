package tafto

import cats.implicits._
import cats.MonadThrow

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

package object util:
  type NonEmpty = MinLength[1]
  type SizedBetween[Min <: Int, Max <: Int] = MinLength[Min] & MaxLength[Max]
  type NonEmptyString = String :| NonEmpty

  opaque type Host = NonEmptyString
  object Host extends RefinedTypeOps[String, NonEmpty, Host]

  type ValidPortRange = GreaterEqual[0] & LessEqual[65535]
  opaque type Port = Int :| ValidPortRange
  object Port extends RefinedTypeOps[Int, ValidPortRange, Port]

  def safeAssert[F[_]: MonadThrow](cond: Boolean, error: => String): F[Unit] =
    if (cond) ().pure else MonadThrow[F].raiseError(new RuntimeException(error))
