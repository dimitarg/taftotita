package tafto.persist.codecs.channel

import cats.implicits.*
import fs2.Chunk
import io.github.iltotore.iron.*
import tafto.domain.EmailMessage

trait ChannelEncoder[A]:
  def encode(a: A): String

object ChannelEncoder:
  val emailMessageIds: ChannelEncoder[List[EmailMessage.Id]] = xs => xs.mkString(",")

trait ChannelDecoder[A]:
  def decode(x: String): Either[String, A]

object ChannelDecoder:
  val emailMessageIds: ChannelDecoder[Chunk[EmailMessage.Id]] = x =>
    val segments = x.split(",")
    Chunk
      .array(segments)
      .traverse(segment =>
        val num = segment.toLongOption.toRight(s"$segment is not an integer.")
        num.map(n => EmailMessage.Id(n))
      )
