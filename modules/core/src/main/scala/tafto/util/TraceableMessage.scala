package tafto.util

import cats.implicits.*
import cats.{Eq, Functor, Show}
import org.typelevel.otel4s.trace.Tracer
import tafto.util.tracing.getTraceContext

final case class TraceableMessage[A](
    traceContext: Map[String, String],
    payload: A
):
  def map[B](f: A => B): TraceableMessage[B] = TraceableMessage(traceContext, f(payload))

object TraceableMessage:
  def make[F[_]: Tracer: Functor, A](payload: A): F[TraceableMessage[A]] =
    getTraceContext[F].map { ctx =>
      TraceableMessage(ctx, payload)
    }

  given show[A: Show]: Show[TraceableMessage[A]] = Show.show { x =>
    show"TraceableMessage(kernel: ${x.traceContext}, payload: ${x.payload})"
  }

  given eq[A: Eq]: Eq[TraceableMessage[A]] = Eq.instance { (x, y) =>
    x.traceContext === y.traceContext && x.payload === y.payload
  }
