package tafto.persist

import cats.effect.*
import fs2.Stream
import skunk.data.Identifier
import skunk.data.Notification

object TestChannelListener:
  def make[F[_]](database: Database[F], channelId: Identifier): Resource[F, Stream[F, String]] = for
    session <- database.pool
    channelStream <- session.channel(channelId).listenR(1)
    result = channelStream.map(_.value)
  yield result

  def stream[F[_]: MonadCancelThrow](database: Database[F], channelId: Identifier): Stream[F, Notification[String]] =
    Stream.resource(database.pool).flatMap(_.channel(channelId).listen(10000))
