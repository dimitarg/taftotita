package tafto.prob.examples

import tafto.prob.*
import tafto.prob.Dist.*

// Consider the simple example of tree growth. Assume a tree can grow between
// one and five feet in height every year. Also assume that it is possible, although less
// likely, that a tree could fall down in a storm or be hit by lightning, which we assume
// would kill it standing. How can this be represented using probabilistic functions?

object TreeGrowth:
  type Height = Int
  // data Tree = Alive Height | Hit Height | Fallen
  enum Tree:
    case Alive(h: Height)
    case Hit(h: Height)
    case Fallen

  // grow :: Trans Tree
  // grow (Alive h) = normal [Alive k | k <- [h+1..h+5]]
  val grow: Transition[Tree] = _ match
    case Tree.Alive(h) =>
      val growsBy = List.range(1, 6)
      val grows = growsBy.map(x => Tree.Alive(h + x))
      normal(grows)
    // unreachable and modeled via non-totality in the haskell impl
    case x => certainly(x)

  // hit :: Trans Tree
  // hit (Alive h) = certainly (Hit h)
  val hit: Transition[Tree] = _ match
    case Tree.Alive(h) => certainly(Tree.Hit(h))
    case x             => certainly(x)

  // fall :: Trans Tree
  // fall _ = certainly Fallen
  val fall: Transition[Tree] = _ => certainly(Tree.Fallen)

  // evolve :: Trans Tree
  // evolve t@(Alive _) = unfoldT (enum [0.9,0.04,0.06] [grow,hit,fall]) t
  // evolve t = certainly t

  // a single cycle / evolution step, which for a live tree can be grow, hit or fall ,according to the probabilities below
  // for a non-live tree, no-op
  val evolve: Transition[Tree] = _ match
    case t: Tree.Alive =>
      val next = Dist(
        List(
          grow -> Probability(0.9),
          hit -> Probability(0.04),
          fall -> Probability(0.06)
        )
      )
      unfoldT(next)(t)
    case t => certainly(t)

  // evolve a tree for n cycles
  def tree(n: Int)(t: Tree): Transition[Tree] = iterate(n)(evolve)
