package tafto.persist
import cats.implicits.*
import skunk.*
import cats.effect.*
import cats.effect.std.Console
import tafto.config.DatabaseConfig
import fs2.io.net.Network
import natchez.Trace

final case class Database[F[_]: MonadCancelThrow](mkSession: Resource[F, Session[F]]):

  def transact[A](body: Session[F] => F[A]): F[A] =
    val transactionalSession = for {
      session <- mkSession
      transaction <- session.transaction
    } yield (session, transaction)
    transactionalSession.use { case (session, _) =>
      body(session)
    }

  def executeCommand[A](command: Command[A])(args: A): F[Unit] = mkSession.use(_.execute(command)(args)).void

  def executeCommand(command: Command[Void]): F[Unit] = mkSession.use(_.execute(command)).void

object Database:
  def make[F[_]: Temporal: Trace: Network: Console](config: DatabaseConfig): Resource[F, Database[F]] =
    Session
      .pooled[F](
        host = config.host.value,
        port = config.port.value,
        user = config.userName.value,
        password = config.password.value.value.some,
        database = config.database.value,
        max = 10
      )
      .map(Database(_))
