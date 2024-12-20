package tafto.util

trait StringEncoder[A]:
  def encode(a: A): String
  def contramap[B](f: B => A): StringEncoder[B] = x => encode(f(x))

trait StringDecoder[A]:
  def decode(x: String): Either[String, A]
  def map[B](f: A => B): StringDecoder[B] = x => decode(x).map(f)

/** A named tuple of an encoder and decoder. For performance reasons at usage sites, it's allowed that the encoded and
  * decoded type are different - in that case, an isomporphism should exist between the two representations, however we
  * don't enforce this via the type system.
  */
final case class StringCodec[A, B](
    encoder: StringEncoder[A],
    decoder: StringDecoder[B]
)
