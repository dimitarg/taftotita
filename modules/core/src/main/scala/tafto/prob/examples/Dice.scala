package tafto.prob.examples

import cats.implicits.*
import tafto.prob.*
import tafto.prob.Dist.*

object Dice:
  val die: Dist[Int] = Dist.uniform(List(1, 2, 3, 4, 5, 6))

  def dice(n: Int): Dist[List[Int]] = n match
    case 0 => certainly(List.empty)
    case _ => die.joinWith(dice(n - 1))(_ :: _)

  def main(args: Array[String]): Unit =

    println(die)

    val isEven: Event[Int] = x => x % 2 === 0
    println(die ?? isEven)

    println(dice(2).dist.size)

    val throwFourDice = dice(4)
    def atLeastTwoSixes(theDice: List[Int]): Boolean =
      theDice.filter(_ === 6).size >= 2

    // what is the probability of getting at least 2  sixes when throwing 4 dice?
    println(throwFourDice ?? atLeastTwoSixes)
