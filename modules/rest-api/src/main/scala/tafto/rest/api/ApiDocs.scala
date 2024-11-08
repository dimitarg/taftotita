package tafto.rest.api

import endpoints4s.openapi
import endpoints4s.openapi.model.{Info, OpenApi}

object ApiDocs extends AllEndpoints with openapi.Endpoints with openapi.JsonEntitiesFromSchemas:

  val api: OpenApi = openApi(
    Info(title = "Tafto REST API", version = "1.0")
  )(
    getHealth
  )
