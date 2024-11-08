package tafto.persist

import skunk.implicits.*
import tafto.service.HealthService

final case class PgHealthService[F[_]](
    database: Database[F]
) extends HealthService[F]:
  override def getHealth: F[Unit] =
    database.executeCommand(HealthQueries.select1)

object HealthQueries:
  val select1 = sql"select 1".command
