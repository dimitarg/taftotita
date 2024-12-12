package tafto.persist

import cats.effect.*
import cats.implicits.*
import ciris.Secret
import fs2.{io as _, *}
import io.github.iltotore.iron.cats.given
import io.github.iltotore.iron.{cats as _, *}
import tafto.domain.*
import tafto.service.PasswordHasher
import tafto.util.NonEmptyString
import weaver.pure.*

object InitSuperAdminTests:

  def tests(db: Database[IO]): Stream[IO, Test] =
    val hasher = new PasswordHasher[IO]:
      override def hashPassword(algo: PasswordHashAlgo, password: UserPassword): IO[HashedUserPassword] =
        IO.pure(HashedUserPassword(PasswordHashAlgo.Bcrypt, password.value))
    val repo = PgUserRepo(db, hasher)

    seqSuite(
      List(
        test("PgUserRepo.initSuperAdmin inits admin only once") {
          for
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
          yield expect(wasInit === true && wasInitTwice === false)
        }
      )
    )
