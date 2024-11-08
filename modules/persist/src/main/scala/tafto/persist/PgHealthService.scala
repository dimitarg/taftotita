package tafto.persist

import cats.implicits.*
import cats.effect.*
import skunk.implicits.*
import skunk.codec.all as skunkCodecs
import tafto.service.HealthService
import tafto.persist.HealthQueries.select1

final case class PgHealthService[F[_]: MonadCancelThrow](
    database: Database[F]
) extends HealthService[F]:
  override def getHealth: F[Unit] =
    database.pool.use { session =>
      session.unique(select1)
    }.void

object HealthQueries:
  val select1 = sql"select 1;".query(skunkCodecs.int4)
