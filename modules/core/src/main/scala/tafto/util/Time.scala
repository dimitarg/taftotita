package tafto.util

import cats.Functor
import cats.effect.Clock
import cats.implicits.*

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

trait Time[F[_]]:
  def instant: F[Instant]
  def utc: F[OffsetDateTime]

object Time:
  def apply[F[_]](using time: Time[F]): Time[F] = time
  given fromClock[F[_]: Clock: Functor]: Time[F] = new Time[F]:
    override def instant: F[Instant] = Clock[F].realTime.map { x =>
      Instant.ofEpochMilli(x.toMillis)
    }
    override def utc: F[OffsetDateTime] = instant.map { i =>
      OffsetDateTime.now(ZoneOffset.UTC)
    }
