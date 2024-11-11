package tafto.rest.api
import tafto.rest.api.resources.*
import BaseEndpoint.*
import sttp.tapir.*
import sttp.tapir.json.circe.*
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
