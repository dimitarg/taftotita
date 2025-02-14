package tafto

import cats.effect.IO
import io.odin.Logger
import weaver.pure.Test

package object testutil:
  extension (test: IO[Test])
    def logDuration(using logger: Logger[IO]): IO[Test] = test.timed
      .flatTap { (duration, test) =>
        logger.info(s"${test.name.name} - took ${duration.toMillis} ms")
      }
      .map { case (_, test) => test }
