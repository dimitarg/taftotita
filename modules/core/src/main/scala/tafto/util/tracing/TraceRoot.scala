package tafto.util.tracing

import cats.effect.MonadCancelThrow
import cats.mtl.Local
import natchez.{EntryPoint, Span, Trace}

trait TraceRoot[F[_]]:
  val trace: Trace[F]
  val ep: EntryPoint[F]
  val local: SpanLocal[F]

  def inRootSpan[A](name: String)(prg: F[A])(using mc: MonadCancelThrow[F]): F[A] =
    ep.root(name).use { root =>
      local.scope(prg)(root)
    }

object TraceRoot:
  def make[F[_]: Trace: EntryPoint: SpanLocal]: TraceRoot[F] = new TraceRoot[F]:
    val trace = Trace[F]
    val ep = summon[EntryPoint[F]]
    val local = Local[F, Span[F]]

  def apply[F[_]: TraceRoot]: TraceRoot[F] = summon[TraceRoot[F]]
