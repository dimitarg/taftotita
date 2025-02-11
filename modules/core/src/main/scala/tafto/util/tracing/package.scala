package tafto.util

import cats.arrow.FunctionK
import cats.data.Kleisli
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits.*
import cats.{Monad, ~>}
import io.odin.Logger
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.oteljava.OtelJava
import org.typelevel.otel4s.oteljava.context.Context
import org.typelevel.otel4s.trace.Tracer

import scala.jdk.CollectionConverters.MapHasAsJava

package object tracing:

  // given idTraceable: TraceableValue[EmailMessage.Id] = x => TraceValue.NumberValue(x.value)

  case class OtlpConfig(
      endpoint: String,
      headers: Map[String, String]
  )

  type TracedIO[A] = Kleisli[IO, Context, A]

  object TracedIO:
    def lift[A](prg: IO[A]): TracedIO[A] = Kleisli.liftF(prg)

  def runInRootContext: TracedIO ~> IO = FunctionK.liftFunction[TracedIO, IO](x => x.run(Context.root))

  private def renderAttributes(attrs: Map[String, String]): String =
    attrs.toList
      .map { (k, v) => s"$k=$v" }
      .mkString(",")

  def mkTracer(
      serviceName: String,
      staticFields: Map[String, String],
      otlpConfig: OtlpConfig
  )(using logger: Logger[TracedIO]) =
    OtelJava
      .autoConfigured[TracedIO] { builder =>
        builder.addPropertiesSupplier { () =>
          Map(
            // this one is probably redundant - should be the case we're not using the global otel instance
            "otel.java.global-autoconfigure.enabled" -> true.show,
            "otel.service.name" -> serviceName,
            "otel.resource.attributes" -> renderAttributes(staticFields),
            "otel.exporter.otlp.endpoint" -> otlpConfig.endpoint,
            "otel.exporter.otlp.headers" -> renderAttributes(otlpConfig.headers),
            // default configuration of otel uses BatchSpanProcessor which keeps spans in a bounded queue
            // the default is 2048 elements which will cause span loss
            "otel.bsp.max.queue.size" -> 1073741824.show // 2 ^ 30

          ).asJava
        }
      }
      .evalMap { otel =>
        logger.info(s"OtelJava for $serviceName: $otel") >>
          otel.tracerProvider.get("tafto.instrument")
      }

  def mkHoneycombTracer(
      config: HoneycombConfig
  )(using logger: Logger[TracedIO]): Resource[TracedIO, Tracer[TracedIO]] =
    val otlpConfig = OtlpConfig(
      endpoint = "https://api.honeycomb.io",
      headers = Map(
        "x-honeycomb-team" -> config.apiKey.value
      )
    )
    mkTracer(
      serviceName = config.serviceName,
      staticFields = config.globalFields,
      otlpConfig = otlpConfig
    )

  def mkHoneycombTracer(
      serviceName: String,
      globalFields: Map[String, String]
  )(using logger: Logger[TracedIO]): Resource[TracedIO, Tracer[TracedIO]] =
    Resource
      .eval(HoneycombConfig.load(serviceName, globalFields).load[TracedIO])
      .flatMap(config => mkHoneycombTracer(config))

  def getTraceContext[F[_]: Tracer]: F[Map[String, String]] = Tracer[F].propagate(Map.empty[String, String])

  extension [F[_]: Monad](tracer: Tracer[F])
    inline def addAttribute[A](inline attribute: Attribute[A]): F[Unit] =
      tracer.currentSpanOrNoop.flatMap { span =>
        span.addAttribute(attribute)
      }
