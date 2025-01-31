package tafto.persist.testutil

import _root_.cats.data.NonEmptyList
import io.github.iltotore.iron.given
import tafto.domain.EmailMessage
import tafto.util.{StringCodec, StringDecoder, StringEncoder, TraceableMessage}

object ChannelCodecs:

  val emailMessageIdsEncoder: StringEncoder[NonEmptyList[EmailMessage.Id]] = xs => xs.toList.mkString(",")
  val emailMessageIdsDecoder: StringDecoder[NonEmptyList[EmailMessage.Id]] = x =>
    val segments = x.split(",")
    for
      segmentsNel <- NonEmptyList
        .fromList(segments.toList)
        .toRight(s"Could not parse non-empty list from payload '$x'")
      result <- segmentsNel.traverse { segment =>
        val num = segment.toLongOption.toRight(s"$segment is not an integer.")
        num.map(n => EmailMessage.Id(n))
      }
    yield result

  val simple: StringCodec[
    TraceableMessage[NonEmptyList[EmailMessage.Id]],
    TraceableMessage[NonEmptyList[EmailMessage.Id]]
  ] = StringCodec(
    encoder = emailMessageIdsEncoder.contramap(_.payload),
    decoder = emailMessageIdsDecoder.map(xs => TraceableMessage(Map.empty, xs))
  )
