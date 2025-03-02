package tafto.prob.examples

import cats.Eq
import cats.implicits.*
import tafto.prob.*
import tafto.prob.Dist.*

object MontyHall1:
  enum Outcome:
    case Win, Lose
  object Outcome:
    given Eq[Outcome] = Eq.fromUniversalEquals

  import Outcome.*

  val firstChoice: Dist[Outcome] = uniform(List(Win, Lose, Lose))
  val switch: Transition[Outcome] = _ match
    case Win  => certainly(Lose)
    case Lose => certainly(Win)

  def main(args: Array[String]): Unit =
    println(firstChoice.normalise)
    println((firstChoice >>= switch).normalise)

