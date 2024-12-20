package tafto.persist.testutil
import fs2.Chunk
import io.github.iltotore.iron.given
import tafto.domain.EmailMessage
import tafto.util.{StringCodec, StringDecoder, StringEncoder, TraceableMessage}

object ChannelCodecs:

  val emailMessageIdsEncoder: StringEncoder[List[EmailMessage.Id]] = xs => xs.mkString(",")
  val emailMessageIdsDecoder: StringDecoder[Chunk[EmailMessage.Id]] = x =>
    val segments = x.split(",")
    Chunk
      .array(segments)
      .traverse(segment =>
        val num = segment.toLongOption.toRight(s"$segment is not an integer.")
        num.map(n => EmailMessage.Id(n))
      )

  val simple: StringCodec[
    TraceableMessage[List[EmailMessage.Id]],
    TraceableMessage[Chunk[EmailMessage.Id]]
  ] = StringCodec(
    encoder = emailMessageIdsEncoder.contramap(_.payload),
    decoder = emailMessageIdsDecoder.map(xs => TraceableMessage(Map.empty, xs))
  )
