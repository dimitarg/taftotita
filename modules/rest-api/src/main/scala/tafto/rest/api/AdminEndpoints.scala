package tafto.rest.api
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*
import tafto.rest.api.resources.*

import BaseEndpoint.*
import InitSuperAdminRequest.given

object AdminEndpoints:

  def adminBase(name: String, desc: String) = base(name, desc)
    .in("admin")

  val initSuperAdmin = adminBase(
    name = "initSuperAdmin",
    desc = "Inits a super-admin user for the system, if not already present."
  )
    .in("init-super-admin")
    .post
    .in(jsonBody[InitSuperAdminRequest])
    .out(statusCode(StatusCode.NoContent).description("The specified super admin user was successfully initialised"))
    .errorOut(
      oneOf[ClientError](
        oneOfVariant[ClientError.Conflict](statusCode(StatusCode.Conflict).and(jsonBody[ClientError.Conflict]))
      )
    )
