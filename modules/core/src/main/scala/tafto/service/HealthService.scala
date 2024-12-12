package tafto.service

import cats.Parallel
import cats.implicits.*

trait HealthService[F[_]]:
  def getHealth: F[Unit]

object HealthService:
  def parallel[F[_]: Parallel](checks: List[HealthService[F]]): HealthService[F] = new HealthService[F]:
    override def getHealth: F[Unit] = checks.parTraverse_(_.getHealth)
