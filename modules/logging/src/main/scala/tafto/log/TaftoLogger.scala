package tafto.log

import cats.effect.{Sync, IO}
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import io.odin._
import io.odin.slf4j.OdinLoggerServiceProvider

class TaftoLogger extends OdinLoggerServiceProvider[IO]:

  implicit val F: Sync[IO] = IO.asyncForIO
  implicit val dispatcher: Dispatcher[IO] = Dispatcher.sequential[IO].allocated.unsafeRunSync()._1

  val loggers: PartialFunction[String, Logger[IO]] = 
    case "some.external.package.SpecificClass" =>
      consoleLogger[IO](minLevel = Level.Warn) //disable noisy external logs
    case _ => //if wildcard case isn't provided, default logger is no-op
      consoleLogger[IO](minLevel = Level.Info)

