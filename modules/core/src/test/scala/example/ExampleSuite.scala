package example

import cats.implicits.*
import cats.effect.*
import fs2.Stream
import weaver.pure.*

object ExampleSuite extends Suite:

  override def suitesStream: fs2.Stream[IO, Test] = Stream(
    pureTest("example test") {
      expect(1 === 1)
    }
  )
