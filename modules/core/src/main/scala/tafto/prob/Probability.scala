package tafto.prob

import cats.kernel.Order

// TODO can be made opaque type
// TODO can use refinement  p in [0.0..1.0]
final case class Probability(p: Double)

object Probability:
  given order: Order[Probability] = Order.by(_.p)
  given Ordering[Probability] = order.toOrdering
