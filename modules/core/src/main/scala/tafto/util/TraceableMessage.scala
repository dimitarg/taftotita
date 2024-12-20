package tafto.util

import cats.Functor
import cats.implicits.*
import natchez.Trace
import org.typelevel.ci.CIString

final case class TraceableMessage[A](
    kernel: Map[CIString, String],
    payload: A
)

object TraceableMessage:
  def make[F[_]: Trace: Functor, A](payload: A): F[TraceableMessage[A]] =
    Trace[F].kernel.map { kernel =>
      TraceableMessage(kernel.toHeaders, payload)
    }
