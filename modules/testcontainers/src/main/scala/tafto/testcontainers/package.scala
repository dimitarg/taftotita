package tafto

import cats.implicits.*
import tafto.util.*
import com.dimafeng.testcontainers.*
import cats.effect.IO

package object testcontainers:

  extension (container: SingleContainer[_])

    def refineHost: Either[String, Host] =
      Host.either(container.host)

    def refinePort(containerPort: Int): Either[String, Port] =
      Port.either(container.mappedPort(containerPort))

  extension [A](either: Either[String, A])
    def errorAsThrowable: Either[Throwable, A] = either.leftMap(x => new RuntimeException(x)).leftWiden[Throwable]
    def asIO: IO[A] = IO.fromEither(errorAsThrowable)
