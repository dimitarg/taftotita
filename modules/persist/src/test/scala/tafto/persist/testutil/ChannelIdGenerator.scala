package tafto.persist.testutil

import cats.implicits.*
import cats.effect.*
import cats.MonadThrow
import tafto.persist.ChannelId
import skunk.data.Identifier
import io.github.iltotore.iron.*
import tafto.persist.ValidChannelId
import tafto.util.*

final case class ChannelIdGenerator[F[_]: MonadThrow](
    private val ref: Ref[F, Int]
):
  def next: F[Identifier] = for
    nextInt <- ref.getAndUpdate(_ + 1)
    (chanId: (String :| ValidChannelId)) <- s"test_channel_$nextInt".refineEither[ValidChannelId].orThrow[F]
    result <- ChannelId.apply(chanId).orThrow[F]
  yield result

object ChannelIdGenerator:
  def make[F[_]: Sync]: F[ChannelIdGenerator[F]] =
    Ref.of[F, Int](0).map(ChannelIdGenerator.apply)
