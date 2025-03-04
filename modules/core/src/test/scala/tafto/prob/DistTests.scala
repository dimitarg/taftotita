package tafto.prob

import cats.effect.IO
import cats.implicits.*
import fs2.Stream
import tafto.prob.testutil.probEquals
import weaver.pure.*

object DistTests extends Suite:

  override def suitesStream: Stream[IO, Test] = Stream(
    pureTest("Dist.scale - example 1") {
      val xs = List(
        1 -> Probability(0.05),
        2 -> Probability(0.2),
        3 -> Probability(0.25)
      )

      val expected = List(
        1 -> Probability(0.1),
        2 -> Probability(0.4),
        3 -> Probability(0.5)
      )

      val scaled = Dist.scale(xs)
      expect(scaled.dist === expected)
    },
    pureTest("Dist.scale - empty") {
      expect(Dist.scale[Int](List()) === Dist.never)
    },
    pureTest("Dist.shape - empty") {
      expect(Dist.shape[Int](_ * 5)(List()) === Dist.never)
    },
    pureTest("Dist.shape - const 1") {
      val result = Dist.shape(_ => 1)(List(1, 2, 3, 4, 5))
      expect(result.dist.map(_._2.p).sum === 1.0) &&
      expect(result === Dist.uniform(List(1, 2, 3, 4, 5)))
    },
    pureTest("normal curve - example 1") {
      val f = normalCurve(mean = 6, stdDev = 2)
      val xs = List.range(1, 12).map(_.toDouble).map(f)
      println(xs)
      success
    },
    pureTest("Dist.normal - behaves like normal distribution") {
      val result = Dist.normal(List(1, 2, 3, 4, 5))
      val eventsByIncreasingProbability =
        result.dist
          .sortBy(_._2.p)
          .map(_._1)

      expect(probEquals(result.dist.map(_._2.p).sum, 1.0)) &&
      expect(result.dist.size === 5) &&
      expect(eventsByIncreasingProbability === List(1, 5, 2, 4, 3))
    }
  )
