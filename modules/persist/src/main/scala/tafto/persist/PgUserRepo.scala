package tafto.persist

import tafto.service.UserRepo
import tafto.domain.*
import tafto.util.NonEmptyString
import skunk.implicits.*
import skunk.codec.all as skunkCodecs
import cats.implicits.*
import cats.effect.MonadCancelThrow

final case class PgUserRepo[F[_]: MonadCancelThrow](database: Database[F]) extends UserRepo[F]:
  import PgUserRepo.*
  override def initSuperAdmin(email: Email, fullName: Option[NonEmptyString], password: UserPassword): F[Boolean] =
    database.transact { session =>
      for {
        alreadyExists <- session.unique(UserQueries.roleExists)(UserRole.SuperAdmin)
        willCreate = !alreadyExists
        _ <-
          if (willCreate) {
            for {
              userId <- session.unique(UserQueries.insertUser)((fullName, email))
              _ <- session.execute(UserQueries.insertUserRole)((userId, UserRole.SuperAdmin))
            } yield ()
          } else {
            ().pure
          }
      } yield willCreate
    }

object UserQueries:
  val roleExists = sql"""
      select exists(
        select 1 from users u
        join user_roles ur on u.id = ur.user_id
        where ur.role = ${codecs.userRole}
      );
    """.query(skunkCodecs.bool)

  val insertUser = sql"""
      insert into users(full_name, email) values (${codecs.nonEmptyText.opt}, ${codecs.email}) returning id;
    """.query(skunkCodecs.int8)

  val insertUserRole = sql"""
      insert into user_roles(user_id, role) values (${skunkCodecs.int8}, ${codecs.userRole});
    """.command
