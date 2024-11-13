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
import tafto.service.PasswordHasher

object InitSuperAdminTests:

  def tests(db: Database[IO]): Stream[IO, Test] =
    val hasher = new PasswordHasher[IO] {
      override def hashPassword(algo: PasswordHashAlgo, password: UserPassword): IO[HashedUserPassword] =
        IO.pure(HashedUserPassword(PasswordHashAlgo.Bcrypt, password.value))
    }
    val repo = PgUserRepo(db, hasher)

    seqSuite(
      List(
        test("PgUserRepo.initSuperAdmin inits admin only once") {
          for {
            wasInit <- repo.initSuperAdmin(
              email = Email("foo@example.com"),
              fullName = None,
              password = Secret("shrubbery123123")
            )
            wasInitTwice <- repo.initSuperAdmin(
              email = Email("foo@example.com"),
              fullName = None,
              password = Secret("shrubbery123123")
            )
          } yield expect(wasInit === true && wasInitTwice === false)
        }
      )
    )
