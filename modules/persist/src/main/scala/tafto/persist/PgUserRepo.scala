package tafto.persist

import tafto.service.UserRepo
import tafto.domain.*
import tafto.util.NonEmptyString
import skunk.implicits.*
import skunk.codec
import cats.implicits.*
import cats.effect.MonadCancelThrow
import cats.Applicative

final case class PgUserRepo[F[_]: MonadCancelThrow](database: Database[F]) extends UserRepo[F]:
  import PgUserRepo.*
  override def initSuperAdmin(email: Email, fullName: Option[NonEmptyString], password: UserPassword): F[Boolean] =
    database.transact { session =>
      for {
        alreadyExists <- session.unique(Queries.roleExists)(UserRole.SuperAdmin.value)
        willCreate = !alreadyExists
        _ <-
          if (willCreate) {
            for {
              userId <- session.unique(Queries.insertUser)((fullName, email.value))
              _ <- session.execute(Queries.insertUserRole)((userId, UserRole.SuperAdmin.value))
            } yield ()
          } else {
            Applicative[F].unit
          }
      } yield willCreate
    }

object Queries:
  val roleExists = sql"""
      select exists(
        select 1 from users u
        join user_roles ur on u.id = ur.user_id
        where ur.role = ${codec.text.text}
      );
    """.query(codec.boolean.bool)

  val insertUser = sql"""
      insert into users(full_name, email) values (${codec.text.text.opt}, ${codec.text.text}) returning id;
    """.query(codec.numeric.int8)

  val insertUserRole = sql"""
      insert into user_roles(user_id, role) values (${codec.numeric.int8}, ${codec.text.varchar(100)});
    """.command
