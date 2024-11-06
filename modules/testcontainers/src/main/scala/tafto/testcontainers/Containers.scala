package tafto.testcontainers

import cats.effect.*

final case class Containers(
    postgres: Postgres
)

object Containers:
  def make: Resource[IO, Containers] = for {
    pg <- Postgres.make
  } yield Containers(pg)
