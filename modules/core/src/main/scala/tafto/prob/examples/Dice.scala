package tafto.prob.examples

import cats.effect.{IO, IOApp}
import cats.implicits.*
import tafto.prob.*
import tafto.prob.Dist.*

object Dice extends IOApp.Simple:

  val die: Dist[Int] = Dist.uniform(List(1, 2, 3, 4, 5, 6))

  def dice(n: Int): Dist[List[Int]] = n match
    case 0 => certainly(List.empty)
    case _ => die.joinWith(dice(n - 1))(_ :: _)

  val isEven: Event[Int] = x => x % 2 === 0

  def atLeastTwoSixes: Event[List[Int]] = theDice => theDice.filter(_ === 6).size >= 2

  // we throw a die
  // if the die is a six, we win
  // if the die is even and not a six, we lose
  // if the die is odd, we can throw again -> if the die is a six, we win. Otherwise, we lose.
  // what's the probability of winning?

  // die = 6,    => win, p = 1/6
  // die is odd, p = 1/3, then die = 6, p = 1/ 6
  // win = 1/6 + (1/3 * 1/6) = 4 / 18 = 2 / 9 = 0.22222222222

  val game: Transition[Int] = theDie =>
    if theDie === 6 || (theDie % 2 === 0) then theDie.pure[Dist]
    else die

  override def run: IO[Unit] = for

    _ <- IO.println(die)

    _ <- IO.println(die ?? isEven)

    _ <- IO.println(dice(2).dist.size)

    throwFourDice = dice(4)

    // what is the probability of getting at least 2  sixes when throwing 4 dice?
    _ <- IO.println(throwFourDice ?? atLeastTwoSixes)

    _ <- IO.println((die >>= game).normalise)
  yield ()
