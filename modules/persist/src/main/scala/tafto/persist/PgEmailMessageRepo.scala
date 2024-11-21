package tafto.persist

import tafto.domain.*
import fs2.Stream
import cats.implicits.*
import skunk.implicits.*
import skunk.codec.all.*
import tafto.persist.codecs.*
import tafto.util.*
import java.time.OffsetDateTime
import cats.effect.kernel.Clock
import cats.data.NonEmptyList
import skunk.data.Identifier
import cats.effect.kernel.MonadCancelThrow
import io.github.iltotore.iron.autoRefine
import io.github.iltotore.iron.cats.given
import tafto.service.comms.EmailMessageRepo

final case class PgEmailMessageRepo[F[_]: Clock: MonadCancelThrow](
    database: Database[F],
    channelId: Identifier
) extends EmailMessageRepo[F]:
  override def insertMessages(messages: NonEmptyList[EmailMessage]): F[List[EmailMessage.Id]] =
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

  override def getMessage(id: EmailMessage.Id): F[Option[(EmailMessage, EmailStatus)]] =
    database.pool.use { s =>
      s.option(EmailMessageQueries.getMessage)(id)
    }

  override val insertedMessages: Stream[F, EmailMessage.Id] =
    database
      .subscribeToChannel(channelId)
      .evalMap { notification =>
        val payload = notification.value
        payload.toLongOption
          .toRight(s"Expect EmailMessage.Id, got ${payload}")
          .map(EmailMessage.Id(_))
          .orThrow[F]
      }

object EmailMessageQueries {

  val domainEmailMessageCodec =
    (nonEmptyText.opt *: toList(_email) *: toList(_email) *: toList(_email) *: nonEmptyText.opt)
      .to[EmailMessage]

  val insertEmailEncoder = domainEmailMessageCodec *: emailStatus *: int4 *: timestamptz

  def insertMessages(size: Int) = {

    sql"""
      insert into email_messages(subject, to_, cc, bcc, body, status, num_attempts, created_at)
      values ${insertEmailEncoder.values.list(size)}
      returning id;
    """.query(emailMessageId)
  }

  def getMessage = {
    sql"""
      select subject, to_, cc, bcc, body, status from email_messages where id = ${emailMessageId}
    """.query(domainEmailMessageCodec *: emailStatus)
  }
}
