package tafto.persist

import tafto.domain.EmailMessage
import cats.implicits.*
import skunk.implicits.*
import skunk.codec.all.*
import tafto.persist.codecs.*
import tafto.util.*
import java.time.OffsetDateTime
import cats.effect.kernel.Clock
import cats.data.NonEmptyList
// import cats.MonadThrow
import skunk.data.Identifier
import cats.effect.kernel.MonadCancelThrow

final case class PgEmailMessageRepo[F[_]: Clock: MonadCancelThrow](
    database: Database[F],
    channelId: Identifier
):
  def insertMessages(messages: NonEmptyList[EmailMessage]): F[List[PgEmailMessage.Id]] =
    database.transact { s =>
      for
        now <- Time[F].utc
        result <- Database.batched(s)(EmailMessageQueries.insertMessages)(messages.map { x =>
          (x, EmailStatus.Scheduled, 0, now)
        })
        channel = s.channel(channelId)
        _ <- result.traverse_(x => {
          channel.notify(x.value.show)
        })
      yield result
    }

object EmailMessageQueries {

  val domainEmailMessageCodec =
    (nonEmptyText.opt *: toList(_email) *: toList(_email) *: toList(_email) *: nonEmptyText.opt)
      .to[EmailMessage]

  val insertEmailEncoder = domainEmailMessageCodec *: EmailStatus.codec *: int4 *: timestamptz

  def insertMessages(size: Int) = {

    sql"""
      insert into email_messages(subject, to_, cc, bcc, body, status, num_attempts, created_at)
      values ${insertEmailEncoder.values.list(size)}
      returning id;
    """.query(PgEmailMessage.Id.codec)
  }
}
