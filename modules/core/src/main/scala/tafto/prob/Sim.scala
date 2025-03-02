package tafto.prob

import cats.Eq
import cats.effect.IO
import cats.effect.std.Random

type RChange[A] = A => IO[A]

// pick :: Dist a -> R a
// pick d = Random.randomRIO (0,1) >>= return . selectP d
def pick[A](dist: Dist[A])(using
    rand: Random[IO]
): IO[A] = rand.betweenDouble(0.0, 1.0).map { r =>
  dist.selectP(Probability(r))
}

def random[A](t: Transition[A])(using rnd: Random[IO]): RChange[A] =
  t andThen pick

// class Sim c where
// (~.) :: Ord a => Int -> (a -> c a) -> RTrans a
trait Sim[F[_]]:
  def run[A: Eq](n: Int)(f: A => F[A]): A => IO[Dist[A]]

object Sim:
  def apply[F[_]](using sim: Sim[F]) = sim
  // instance Sim IO where
  // (~.) n t = rDist . replicate n . t
  given Sim[IO] = new Sim[IO]:
    override def run[A: Eq](n: Int)(f: A => IO[A]): A => IO[Dist[A]] = a =>
      f(a).replicateA(n).map(xs => Dist.uniform(xs).normalise)

    // (~.) n = (~.) n . random
  given (using Random[IO]): Sim[Dist] = new Sim[Dist]:
    override def run[A: Eq](n: Int)(t: Transition[A]): A => IO[Dist[A]] = a => summon[Sim[IO]].run(n)(random(t))(a)
