package tafto.util

import cats.arrow.FunctionK
import cats.data.Kleisli
import cats.effect.{Async, IO, Resource, Sync}
import cats.implicits.*
import cats.mtl.Local
import cats.{Monad, ~>}
import natchez.honeycomb.Honeycomb
import natchez.noop.NoopSpan
import natchez.{EntryPoint, Span, Trace, TraceValue, TraceableValue}
import tafto.domain.EmailMessage

import scala.jdk.CollectionConverters.*

package object tracing:
  def span[F[_]: Trace: Monad, A](name: String)(fields: (String, TraceValue)*)(prg: F[A]): F[A] =
    Trace[F].span(name = name) {
      for
        _ <- Trace[F].put(fields*)
        result <- prg
      yield result
    }

  given idTraceable: TraceableValue[EmailMessage.Id] = x => TraceValue.NumberValue(x.value)

  type TracedIO[A] = Kleisli[IO, Span[IO], A]

  object TracedIO:
    def lift[A](prg: IO[A]): TracedIO[A] = Kleisli.liftF(prg)

  type SpanLocal[F[_]] = Local[F, Span[F]]

  def withNoSpan: TracedIO ~> IO = FunctionK.liftFunction[TracedIO, IO](x => x.run(NoopSpan()))

  def honeycombEntryPoint[F[_]: Sync](config: HoneycombConfig): Resource[F, EntryPoint[F]] = Honeycomb.entryPoint[F](
    service = config.serviceName
  ) { builder =>
    Sync[F].delay {
      builder
        .setWriteKey(config.apiKey.value)
        .setGlobalFields(config.globalFields.asJava)
        .build()
    }
  }

  def honeycombEntryPoint[F[_]: Async](
      serviceName: String,
      globalFields: Map[String, String]
  ): Resource[F, EntryPoint[F]] =
    for
      config <- Resource.eval(HoneycombConfig.load(serviceName, globalFields).load[F])
      result <- honeycombEntryPoint[F](config)
    yield result
