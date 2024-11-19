package tafto.persist
import cats.implicits.*
import skunk.*
import cats.effect.*
import cats.effect.std.Console
import tafto.config.DatabaseConfig
import fs2.io.net.Network
import natchez.Trace
import cats.data.NonEmptyList
import cats.Applicative

final case class Database[F[_]: MonadCancelThrow](pool: Resource[F, Session[F]]):

  def transact[A](body: Session[F] => F[A]): F[A] =
    val transactionalSession = for {
      session <- pool
      transaction <- session.transaction
    } yield (session, transaction)
    transactionalSession.use { case (session, _) =>
      body(session)
    }

object Database:
  // error is raised if query has more than 32767 parameters
  // this batch size allows for ~65 query parameters per row, which should be plenty
  val batchSize = 500

  def make[F[_]: Temporal: Trace: Network: Console](config: DatabaseConfig): Resource[F, Database[F]] =
    Session
      .pooled[F](
        host = config.host.value,
        port = config.port.value,
        user = config.userName.value,
        password = config.password.value.value.some,
        database = config.database.value,
        max = 10,
        strategy = Strategy.SearchPath
      )
      .map(Database(_))

  def batched[F[_]: Applicative, A, B](
      s: Session[F]
  )(query: Int => Query[List[A], B])(in: NonEmptyList[A]): F[List[B]] = {
    val inputBatches = in.toList.grouped(batchSize).toList
    inputBatches
      .traverse { xs =>
        s.execute(query(xs.size))(xs)
      }
      .map(_.flatten)
  }
