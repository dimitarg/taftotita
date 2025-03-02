package tafto.prob.examples

import cats.Eq
import cats.implicits.*
import tafto.prob.*
import tafto.prob.Dist.*

enum CoinFlip:
  case Head, Tail
object CoinFlip:
  given Eq[CoinFlip] = Eq.fromUniversalEquals

object Coins:

  import CoinFlip.*

  val coinFlip: Dist[CoinFlip] = uniform(List(Head, Tail))

  val ifHeadFlipAgain: Dist[CoinFlip] = coinFlip.flatMap {
    case Head => certainly(Head)
    case Tail => coinFlip
  }

  def main(args: Array[String]): Unit =
    println(ifHeadFlipAgain.normalise)
