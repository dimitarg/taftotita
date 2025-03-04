package tafto.prob

import cats.implicits.*
import tafto.prob.Dist.certainly

type Event[A] = A => Boolean

// a probabilistic function where source and target are the same
type Transition[A] = A => Dist[A]

// normalCurve :: Float -> Float -> Float -> Float
// normalCurve mean stddev x = 1 / sqrt (2 * pi) * exp (-1/2 * u^2)
// where u = (x - mean) / stddev
def normalCurve(mean: Double, stdDev: Double): Double => Double = x =>
  val u = (x - mean) / stdDev
  (1 / math.sqrt(2 * math.Pi)) * math.exp(-0.5 * u * u)

// unfold a distribution of transitions into one transition
// NOTE: The argument transitions must be independent
// unfoldT :: Dist (Trans a) -> Trans a
// unfoldT (D d) x = D [ (y,p*q) | (f,p) <- d, (y,q) <- unD (f x) ]

def unfoldT[A](d: Dist[Transition[A]]): Transition[A] = a =>
  val result = for
    (f, p) <- d.dist
    (b, q) <- f(a).dist
  yield (b, Probability(p.p * q.p))
  Dist(result)

def iterate[A](n: Int)(f: Transition[A]): Transition[A] = a =>
  if n <= 0 then certainly(a)
  else f(a).flatMap(iterate(n - 1)(f))
