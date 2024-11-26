package tafto.persist

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.*
import _root_.skunk.data.Identifier

type ChannelId = String :| (Trimmed & LettersLowerCase)
object ChannelId {
  def apply(x: String :| (Trimmed & LettersLowerCase)): Either[String, Identifier] = Identifier.fromString(x)
}
