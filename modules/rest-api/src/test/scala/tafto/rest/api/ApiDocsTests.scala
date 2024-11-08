package tafto.rest.api

import weaver.pure.*
import fs2.Stream
import cats.implicits.*
import cats.effect.*
import cats.effect.std.Console
import endpoints4s.openapi.model.OpenApi

object ApiDocsTests extends Suite:
  override def suitesStream: Stream[IO, Test] = parSuite(
    List(
      test("API Docs Generation") {
        val apiString = OpenApi.stringEncoder.encode(ApiDocs.api)
        Console[IO].println(apiString).as(success)
      }
    )
  )
