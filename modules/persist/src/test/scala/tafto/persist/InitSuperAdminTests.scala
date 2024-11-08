package tafto.persist

import cats.implicits.*
import cats.effect.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.cats.given
import fs2.*
import weaver.pure.*

import tafto.domain.*
import tafto.util.NonEmptyString
import ciris.Secret

object InitSuperAdminTests:

  def tests(db: Database[IO]): Stream[IO, Test] =

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
