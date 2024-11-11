package tafto.rest.api

import weaver.pure.*
import fs2.Stream
import cats.implicits.*
import cats.effect.*
import cats.effect.std.Console
import sttp.apispec.openapi.circe.*
import sttp.apispec.openapi.circe.yaml.*

object ApiDocsTests extends Suite:
  override def suitesStream: Stream[IO, Test] = parSuite(
    List(
      test("API Docs Generation") {
        val apiString = ApiDocs.openApi.toYaml
        Console[IO].println(apiString).as(success)
      }
    )
  )
