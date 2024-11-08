package tafto.rest.api

import endpoints4s.algebra.Endpoints
import endpoints4s.algebra.JsonEntitiesFromSchemas

trait BaseEndpoints extends Endpoints with JsonEntitiesFromSchemas {
  val v1: Path[Unit] = path / "v1"
}
