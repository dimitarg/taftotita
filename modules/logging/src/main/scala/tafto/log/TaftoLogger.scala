package tafto.log

import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Sync}
import io.odin.*
import io.odin.slf4j.OdinLoggerServiceProvider

class TaftoLogger extends OdinLoggerServiceProvider[IO]:

  implicit val F: Sync[IO] = IO.asyncForIO
  implicit val dispatcher: Dispatcher[IO] = Dispatcher.sequential[IO].allocated.unsafeRunSync()._1

  val loggers: PartialFunction[String, Logger[IO]] =
    case "some.external.package.SpecificClass" =>
      consoleLogger[IO](minLevel = Level.Warn) // disable noisy external logs
    case x if x.startsWith("io.opentelemetry") =>
      consoleLogger[IO](minLevel = Level.Debug)
    case _ => // if wildcard case isn't provided, default logger is no-op
      consoleLogger[IO](minLevel = Level.Info)
