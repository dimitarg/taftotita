package tafto.prob.examples

import cats.Eq
import cats.implicits.*
import tafto.prob.Dist.*

enum Marble:
  case R, G, B
object Marble:
  given Eq[Marble] = Eq.fromUniversalEquals

object Marbles:
  import Marble.*

  def main(args: Array[String]): Unit =
    // “What is the probability of drawing a red, green, and blue marble (in this order) from a jar containing two
    // red, two green, and one blue marble without putting them back?”

    val selected = select(3)(List(R, R, G, G, B))
    println(selected ?? (xs => xs === List(R, G, B)))
