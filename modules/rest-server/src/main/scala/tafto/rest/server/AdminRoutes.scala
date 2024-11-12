package tafto.rest.server

import tafto.rest.api.AdminEndpoints.*
import tafto.rest.api.resources.ClientError
import cats.implicits.*
import cats.effect.*
import tafto.service.UserRepo

final case class AdminRoutes[F[_]: Concurrent](
    userRepo: UserRepo[F]
):
  val initSuperAdminRoute = initSuperAdmin.serverLogic { req =>
    userRepo.initSuperAdmin(req.email, req.fullName, req.password).map { created =>
      created match
        case true  => ().asRight
        case false => ClientError.Conflict("Super admin user already exists in this system.").asLeft
    }
  }
