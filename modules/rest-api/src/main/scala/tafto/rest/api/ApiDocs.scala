package tafto.rest.api

import sttp.apispec.openapi.OpenAPI
import sttp.tapir.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter

object ApiDocs:
  val openApi: OpenAPI = OpenAPIDocsInterpreter().toOpenAPI(
    es = List(
      HealthEndpoints.getHealth
    ),
    title = "Tafto IDM",
    version = "1.0"
  )
