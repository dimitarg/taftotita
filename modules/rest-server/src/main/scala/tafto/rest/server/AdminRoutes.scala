package tafto.rest.server

import cats.effect.*
import cats.implicits.*
import tafto.rest.api.AdminEndpoints.*
import tafto.rest.api.resources.ClientError
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
