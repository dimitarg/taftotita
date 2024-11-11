package tafto.rest.api

import ciris.Secret
import cats.Show
import sttp.tapir.Schema
import io.circe.*

package object resources:

  given secretSchema[A](using underlying: Schema[A], show: Show[A]): Schema[Secret[A]] =
    underlying.map(x => Some(Secret(x)))(_.value)

  given secretDecoder[A](using underlying: Decoder[A], show: Show[A]): Decoder[Secret[A]] =
    underlying.map(Secret.apply)

  // this should never be reachable, but tapir requires encoder for a component even if it's only used as input
  given secretEncoder[A](using underlying: Encoder[A], show: Show[A]): Encoder[Secret[A]] =
    sys.error("attempted to encode secret as json!")
