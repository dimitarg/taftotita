package tafto.rest.server

import tafto.rest.api.AdminEndpoints.*
import cats.implicits.*
import cats.effect.*
import tafto.service.UserRepo

final case class AdminRoutes[F[_]: Concurrent](
    userRepo: UserRepo[F]
):
  val initSuperAdminRoute = initSuperAdmin.serverLogicSuccess { req =>
    userRepo.initSuperAdmin(req.email, req.fullName, req.password).void
  }
