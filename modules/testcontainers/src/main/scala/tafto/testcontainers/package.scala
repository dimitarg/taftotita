package tafto

import cats.effect.IO
import cats.implicits.*
import com.dimafeng.testcontainers.*
import tafto.util.*

package object testcontainers:

  extension (container: SingleContainer[?])

    def refineHost: Either[String, Host] =
      Host.either(container.host)

    def refinePort(containerPort: Int): Either[String, Port] =
      Port.either(container.mappedPort(containerPort))

  extension [A](either: Either[String, A])
    def errorAsThrowable: Either[Throwable, A] = either.leftMap(x => new RuntimeException(x)).leftWiden[Throwable]
    def asIO: IO[A] = IO.fromEither(errorAsThrowable)
