package tafto.persist
import cats.Applicative
import cats.data.NonEmptyList
import cats.effect.*
import cats.effect.std.Console
import cats.implicits.*
import fs2.Stream
import fs2.io.net.Network
import natchez.Trace
import skunk.*
import skunk.data.Identifier
import skunk.data.Notification
import tafto.config.DatabaseConfig

final case class Database[F[_]: MonadCancelThrow](pool: Resource[F, Session[F]]):

  def transact[A](body: Session[F] => F[A]): F[A] =
    val transactionalSession = for
      session <- pool
      transaction <- session.transaction
    yield (session, transaction)
    transactionalSession.use { case (session, _) =>
      body(session)
    }

  def subscribeToChannel(channelId: Identifier): Stream[F, Notification[String]] =
    Stream.resource(pool).flatMap { session =>
      session.channel(channelId).listen(Database.notificationQueueSize)
    }

object Database:
  // error is raised if query has more than 32767 parameters
  // this batch size allows for ~65 query parameters per row, which should be plenty
  val batchSize = 500
  // this is up to ~80MB memory under the default PG configuration (each message cannot exceed 8000 bytes)
  val notificationQueueSize = 10000

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
  )(query: Int => Query[List[A], B])(in: NonEmptyList[A]): F[List[B]] =
    val inputBatches = in.toList.grouped(batchSize).toList
    inputBatches
      .traverse { xs =>
        s.execute(query(xs.size))(xs)
      }
      .map(_.flatten)
