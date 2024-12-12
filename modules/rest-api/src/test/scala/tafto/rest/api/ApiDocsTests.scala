package tafto.rest.api

import cats.effect.*
import cats.effect.std.Console
import cats.implicits.*
import fs2.Stream
import sttp.apispec.openapi.circe.*
import sttp.apispec.openapi.circe.yaml.*
import weaver.pure.*

object ApiDocsTests extends Suite:
  override def suitesStream: Stream[IO, Test] = parSuite(
    List(
      test("API Docs Generation") {
        val apiString = ApiDocs.openApi.toYaml
        Console[IO].println(apiString).as(success)
      }
    )
  )
