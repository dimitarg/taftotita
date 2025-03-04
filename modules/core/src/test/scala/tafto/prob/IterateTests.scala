package tafto.prob

import cats.effect.IO
import cats.implicits.*
import fs2.Stream
import tafto.prob.Dist.*
import weaver.pure.*

object IterateTests extends Suite:

  override def suitesStream: Stream[IO, Test] = Stream(
    pureTest("iterate with 0 is no-op") {
      val trans: Transition[Int] = _ => ???
      val iter = iterate(0)(trans)
      val a = 1

      expect(iter(a) === certainly(a))
    },
    pureTest("iterate with 1 and trans is trans") {
      val trans: Transition[Int] = x => uniform(List(x * 2, x * 4))
      val iter = iterate(1)(trans)
      val a = 1

      expect(iter(a) === trans(a))
    },
    pureTest("iterate iterates flatMap") {
      val trans: Transition[Int] = x => uniform(List(x * 2, x * 4))
      val iter = iterate(2)(trans)
      val a = 1

      val result = iter(a).normalise

      // 2 -> 0.5, 4 -> 0.5
      //  4 -> 0.25, 8 -> 0.25
      //  8 -> 0.25, 16 -> 0.25
      val expected = Dist(
        List(
          4 -> Probability(0.25),
          8 -> Probability(0.5),
          16 -> Probability(0.25)
        )
      )

      expect(result === expected)
    }
  )
