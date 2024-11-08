package tafto.persist

import cats.effect.*
import fs2.*
import weaver.pure.*

object PgHealthServiceTests:

  def tests(db: Database[IO]): Stream[IO, Test] =

    val service = PgHealthService(db)

    parSuite(
      List(
        test("health check is successful") {
          service.getHealth.as(success)
        }
      )
    )
