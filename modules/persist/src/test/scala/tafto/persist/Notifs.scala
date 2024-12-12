package tafto.persist

import cats.effect.*
import cats.implicits.*
import fs2.Stream
import natchez.Trace.Implicits.noop
import skunk.data.Identifier
import tafto.db.DatabaseMigrator
import tafto.testcontainers.Postgres

import scala.concurrent.duration.*

object Notifs extends IOApp.Simple:

  val dbResource: Resource[IO, Database[IO]] = for
    pg <- Postgres.make(dataBind = None, tailLog = true)
    config = pg.databaseConfig
    db <- Database.make[IO](config)
    _ <- Resource.eval(DatabaseMigrator.migrate[IO](config))
  yield db

  // this fucking thing needs to be lowercase.
  val chanId = Identifier.fromString("asd").toOption.get

  def producer(db: Database[IO]): Stream[IO, Unit] =
    Stream
      .resource(db.pool)
      .flatMap { session =>
        val channel = session.channel(chanId)
        val messages = Stream
          .awakeEvery[IO](1.second)
          .map(_.show)

        channel(messages)
          .evalTap(_ => IO.println("Published message."))
      }

  def consumer(db: Database[IO]): Stream[IO, Unit] =
    TestChannelListener
      .stream(db, chanId)
      .evalMap(n => IO.println(s"received $n"))

  def test(db: Database[IO]): IO[Unit] =
    producer(db).concurrently(consumer(db)).compile.drain

  override def run: IO[Unit] = dbResource.use(test)
