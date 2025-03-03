package tafto.prob.examples

import cats.Eq
import cats.effect.std.Random
import cats.effect.{IO, IOApp}
import cats.implicits.*
import tafto.prob.*
import tafto.prob.Dist.*

object MontyHall1 extends IOApp.Simple:

  enum Outcome:
    case Win, Lose
  object Outcome:
    given Eq[Outcome] = Eq.fromUniversalEquals

  import Outcome.*

  val firstChoice: Dist[Outcome] = uniform(List(Win, Lose, Lose))

  val switch: Transition[Outcome] = _ match
    case Win  => certainly(Lose)
    case Lose => certainly(Win)

  val stay: Transition[Outcome] = x => certainly(x)

  def avg[A: Eq](k: Int)(dist: Dist[A])(f: RTransition[A])(using Random[IO]): RDist[A] =
    pick(dist)
      .replicateA(k)
      .flatMap { xs =>
        xs.traverse(x => f(x))
      }
      .map { dists =>
        val xs = dists
          .flatMap(_.dist)
          .map { (a, p) => (a, Probability(p.p / dists.size)) }
        Dist(xs).normalise
      }

  override def run: IO[Unit] = for
    _ <- IO.println((firstChoice >>= stay).normalise)
    _ <- IO.println((firstChoice >>= switch).normalise)

    given Random[IO] <- Random.scalaUtilRandom[IO]

    a <- avg(10000)(firstChoice)(Sim[Dist].iterate(1000)(switch))
    b <- avg(10000)(firstChoice)(Sim[Dist].iterate(1000)(stay))
    _ <- IO.println(a)
    _ <- IO.println(b)
  yield ()
