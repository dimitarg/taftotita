package tafto.persist

import cats.effect.*
import cats.implicits.*
import skunk.codec.all as skunkCodecs
import skunk.implicits.*
import tafto.persist.HealthQueries.select1
import tafto.service.HealthService

final case class PgHealthService[F[_]: MonadCancelThrow](
    database: Database[F]
) extends HealthService[F]:
  override def getHealth: F[Unit] =
    database.pool.use { session =>
      session.unique(select1)
    }.void

object HealthQueries:
  val select1 = sql"select 1;".query(skunkCodecs.int4)
