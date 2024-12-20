package tafto.util

import cats.implicits.*
import cats.{Functor, Show}
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

  given show[A: Show]: Show[TraceableMessage[A]] = Show.show { x =>
    show"TraceableMessage(kernel: ${x.kernel}, payload: ${x.payload})"
  }
