package tafto.json

import cats.implicits.*
import fs2.Chunk
import io.circe.Printer.noSpaces
import io.circe.parser.decode
import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.circe.given
import tafto.domain.EmailMessage
import tafto.json.UtilCodecs.given
import tafto.util.{StringCodec, StringDecoder, StringEncoder, TraceableMessage}

object JsonStringCodecs:

  def stringEncoderFromJson[A: Encoder]: StringEncoder[A] = x => Encoder[A].apply(x).printWith(noSpaces)

  def stringDecoderFromJson[A: Decoder]: StringDecoder[A] = x => decode[A](x).leftMap(_.toString())

  val traceableMessageIdsEncoder: StringEncoder[TraceableMessage[List[EmailMessage.Id]]] =
    stringEncoderFromJson

  val traceableMessageIdsDecoder: StringDecoder[TraceableMessage[Chunk[EmailMessage.Id]]] =
    stringDecoderFromJson

  val traceableMessageIdsStringCodec = StringCodec(traceableMessageIdsEncoder, traceableMessageIdsDecoder)
