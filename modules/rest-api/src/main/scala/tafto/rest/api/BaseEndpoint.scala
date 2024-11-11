package tafto.rest.api

import sttp.tapir.*

object BaseEndpoint:
  def base(name: String, desc: String): Endpoint[Unit, Unit, Unit, Unit, Any] =
    endpoint
      .name(name)
      .description(desc)
      .in("v1")
