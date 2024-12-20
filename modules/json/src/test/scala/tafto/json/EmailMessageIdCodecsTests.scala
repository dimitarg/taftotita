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

  override def suitesStream: Stream[IO, Test] = parSuite {

    val maxSupportedMessages = 392
    val expectedSizeLessThan = 8000

    List(
      test(s"Encoding up to ${maxSupportedMessages} messages is less than ${expectedSizeLessThan} bytes") {
        val messageIds = List
          .fill(n = maxSupportedMessages)(elem = Long.MaxValue)
          .map(x => EmailMessage.Id.apply(x))

        val gen = traceableMessageGen(Gen.const(messageIds))

        forall(gen) { message =>
          val encoded = codec.encoder.encode(message)
          val encodedBytes = encoded.getBytes()

          expect(encodedBytes.length < expectedSizeLessThan)
        }
      },
      test("Roundtrip") {
        val gen = traceableMessageGen(Gen.listOf(emailIdGen))
        forall(gen) { message =>
          codec.decoder.decode(codec.encoder.encode(message)).orThrow[IO].map { decoded =>
            expect(decoded.map(_.toList) === message)
          }
        }
      }
    )
  }
