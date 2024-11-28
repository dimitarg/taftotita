package tafto.service.util

import scala.concurrent.duration.*

import retry.*
import cats.Applicative
import cats.effect.Temporal
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*
import io.odin.Logger

object Retry:
  def fullJitter[F[_]: Applicative](
      maxRetries: Int :| Positive,
      baseDelay: FiniteDuration
  ) = RetryPolicies.limitRetries[F](maxRetries) `join` RetryPolicies.fullJitter(baseDelay)

  def retrying[F[_]: Temporal: Logger, A](policy: RetryPolicy[F])(fa: F[A]): F[A] =
    retryingOnAllErrors[A](
      policy = policy,
      onError = (error: Throwable, _: RetryDetails) => {
        Logger[F].warn(error.getMessage(), error)
      }
    )(fa)
