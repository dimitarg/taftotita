package tafto.persist.testutil

import cats.MonadThrow
import cats.effect.*
import cats.implicits.*
import io.github.iltotore.iron.{skunk as _, *}
import skunk.data.Identifier
import tafto.persist.{ChannelId, ValidChannelId}
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
