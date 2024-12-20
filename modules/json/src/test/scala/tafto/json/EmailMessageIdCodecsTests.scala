package tafto.json
import cats.effect.*
import cats.implicits.*
import fs2.Stream
import io.github.iltotore.iron.cats.given
import io.github.iltotore.iron.given
import org.scalacheck.Gen
import tafto.domain.EmailMessage
import tafto.json.JsonStringCodecs.traceableMessageIdsStringCodec as codec
import tafto.testutil.Generators.*
import tafto.util.*
import weaver.pure.*
import weaver.scalacheck.*

object EmailMessageIdCodecsTests extends Suite with Checkers:

  override def suitesStream: Stream[IO, Test] = parSuite(
    List(
      test("traceableMessageIdsStringCodec - encoding up to 392 messages does not exceed 8000 bytes") {
        val messageIds = List
          .fill(n = 392)(elem = Long.MaxValue)
          .map(x => EmailMessage.Id.apply(x))

        val gen = traceableMessageGen(Gen.const(messageIds))

        forall(gen) { message =>
          val encoded = codec.encoder.encode(message)
          val encodedBytes = encoded.getBytes()

          expect(encodedBytes.length < 8000)
        }
      },
      test("traceableMessageIdsStringCodec - roundtrip") {
        val gen = traceableMessageGen(Gen.listOf(emailIdGen))
        forall(gen) { message =>
          codec.decoder.decode(codec.encoder.encode(message)).orThrow[IO].map { decoded =>
            expect(message.kernel === decoded.kernel) `and`
              expect(message.payload === decoded.payload.toList)
          }
        }
      }
    )
  )
