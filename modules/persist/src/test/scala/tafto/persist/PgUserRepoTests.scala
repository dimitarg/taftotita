package tafto.persist

import cats.implicits.*
import cats.effect.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.cats.given
import fs2.*
import weaver.pure.*
import tafto.testcontainers.Postgres

import natchez.Trace.Implicits.noop
import tafto.domain.*
import tafto.util.NonEmptyString
import ciris.Secret
import tafto.db.DatabaseMigrator

object PgUserRepoTests extends Suite {

  val dbResource = for {
    pg <- Postgres.make
    config = pg.databaseConfig
    db <- Database.make(config)
    _ <- Resource.eval(DatabaseMigrator.migrate(config))
  } yield db

  override def suitesStream: Stream[IO, Test] = Stream.resource(dbResource).flatMap { db =>

    val repo = PgUserRepo(db)

    seqSuite(
      List(
        test("PgUserRepo.initSuperAdmin inits admin only once") {
          for {
            wasInit <- repo.initSuperAdmin(
              email = Email("foo@example.com"),
              fullName = None,
              password = Secret("shrubbery")
            )
            wasInitTwice <- repo.initSuperAdmin(
              email = Email("foo@example.com"),
              fullName = None,
              password = Secret("shrubbery")
            )
          } yield expect(wasInit === true && wasInitTwice === false)
        }
      )
    )
  }

}
