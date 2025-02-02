package tafto.service.util

import cats.Applicative
import cats.effect.Temporal
import cats.effect.std.Random
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*
import io.odin.Logger
import retry.*

import scala.concurrent.duration.*

object Retry:
  def fullJitter[F[_]: Applicative: Random](
      maxRetries: Int :| Positive,
      baseDelay: FiniteDuration
  ): RetryPolicy[F, Any] = RetryPolicies.limitRetries[F](maxRetries) `join` RetryPolicies.fullJitter(baseDelay)

  def retrying[F[_]: Temporal: Logger, A](policy: RetryPolicy[F, Throwable])(fa: F[A]): F[A] =
    retryingOnErrors[F, A](fa)(
      policy = policy,
      errorHandler =  ResultHandler.retryOnAllErrors {
        (error: Throwable, _: RetryDetails) => Logger[F].warn(error.getMessage(), error)
      }
    )
