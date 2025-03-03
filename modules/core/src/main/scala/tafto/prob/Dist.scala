package tafto.prob

import cats.implicits.*
import cats.{Eq, Monad}

import scala.annotation.tailrec

// Intuitively we'd like to represent dist as a set, but this creates technical problems as we need a lawful functor instance
// Instead we provide means to normalize a Dist by merging the probabilities of each element
final case class Dist[A](dist: List[(A, Probability)]):

  // combines two independent distributions
  def joinWith[B, C](b: Dist[B])(f: (A, B) => C): Dist[C] =
    val result = for
      (a, pa) <- dist
      (b, pb) <- b.dist
    yield (f(a, b), Probability(pa.p * pb.p))
    Dist(result)

  // prod :: Dist a -> Dist b -> Dist (a,b)
  // prod = joinWith (,)
  def product[B](b: Dist[B]): Dist[(A, B)] = joinWith(b)((_, _))

  def normalise(using eq: Eq[A]): Dist[A] =
    val distinctElements = dist.map(_._1).distinct
    val groupedP = dist.groupMapReduce(_._1)(_._2)((p1, p2) => Probability(p1.p + p2.p))
    Dist(
      distinctElements
        .map(a => a -> groupedP.get(a))
        .collect { case (a, Some(p)) => (a, p) }
    )

  def getProbability(event: Event[A]): Probability =
    Probability(
      dist
        .filter { (outcome, _) =>
          event(outcome)
        }
        .map { (_, p) => p.p }
        .sum
    )

  def ??(event: Event[A]): Probability = getProbability(event)

  def selectP(p: Probability): A = scanP(p.p)

  @tailrec
  // fixme non-total
  def scanP(p: Double): A = dist match
    case (x, q) :: ps => if p <= q.p || ps.isEmpty then x else Dist(ps).scanP((p - q.p))
    case _            => ???

object Dist:

  def certainly[A](a: A): Dist[A] = Dist(
    List(
      a -> Probability(1.0)
    )
  )

  def never[A]: Dist[A] = Dist(List())

  def uniform[A](xs: List[A]): Dist[A] =
    val p = Probability(1.0 / xs.size.toDouble)
    Dist(xs.map(x => x -> p))

  // uniformly select one element, returned along the unselected elements
  def selectOne[A: Eq](xs: List[A]): Dist[(A, List[A])] =
    Dist.uniform(xs).map { x =>
      (x, delete(xs, x))
    }

  // you might be tempted to use filterNot, but that will remove all x occurrences which is incorrect
  private def delete[A: Eq](xs: List[A], x: A): List[A] =
    // TODO this is not pretty
    xs.toBuffer.subtractOne(x).toList

  // uniformly select n out of xs element
  // TODO this is not tail-recursive
  def selectMany[A: Eq](n: Int)(xs: List[A]): Dist[(List[A], List[A])] = n match
    case x if x <= 0 => (List.empty, xs).pure[Dist]
    case _ =>
      for
        (firstSelected, remaining) <- selectOne(xs)
        (restSelected, unselected) <- selectMany(n - 1)(remaining)
      yield (firstSelected :: restSelected, unselected)

  // selectMany, but discard the non-selected elements from the result
  // TODO not sure I understood the paper on why we should reverse here
  def select[A: Eq](n: Int)(xs: List[A]): Dist[List[A]] =
    selectMany(n)(xs)
      .map { (selected, _) => selected }

  def pure[A](a: A): Dist[A] = certainly(a)

  def flatMap[A, B](distA: Dist[A])(f: A => Dist[B]): Dist[B] =
    val result = for
      (a, pa) <- distA.dist
      (b, pb) <- f(a).dist
    yield (b, Probability(pa.p * pb.p))
    Dist(result)

  given Monad[Dist] with

    override def pure[A](x: A): Dist[A] = Dist.pure(x)

    override def flatMap[A, B](fa: Dist[A])(f: A => Dist[B]): Dist[B] = Dist.flatMap(fa)(f)

    // FIXME can't mess with this now
    override def tailRecM[A, B](a: A)(f: A => Dist[Either[A, B]]): Dist[B] = flatMap(f(a)) {
      case Right(b)    => pure(b)
      case Left(nextA) => tailRecM(nextA)(f)
    }
