package tafto.json

import cats.implicits.*
import fs2.Chunk
import io.circe.Decoder.given
import io.circe.Encoder.given
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}
import tafto.util.TraceableMessage

import scala.reflect.ClassTag

object UtilCodecs:

  given traceableMessageEncoder[A: Encoder]: Encoder[TraceableMessage[A]] = Encoder.derived
  given traceableMessageDecoder[A: Decoder]: Decoder[TraceableMessage[A]] = Decoder.derived
  given chunkDecoder[A: Decoder: ClassTag]: Decoder[Chunk[A]] =
    Decoder[Array[A]].map(Chunk.array)
