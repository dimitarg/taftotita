package tafto.prob.examples

import cats.effect.std.Random
import cats.effect.{IO, IOApp}
import cats.implicits.*
import cats.kernel.Eq
import tafto.prob.*
import tafto.prob.Dist.*
import tafto.prob.examples.MontyHall1.Outcome

object Monty2 extends IOApp.Simple:

  enum Door:
    case A, B, C
  object Door:
    given Eq[Door] = Eq.fromUniversalEquals

  import Door.*
  val doors = List(A, B, C)

  // data State = Doors {prize :: Door, chosen :: Door, opened :: Door}
  trait State:
    self =>
    def prize: Door
    def chosen: Door
    def opened: Door

    def withPrize(d: Door): State = new State:
      def prize = d
      def chosen = self.chosen
      def opened = self.opened

    def withChosen(d: Door): State = new State:
      def prize = self.prize
      def chosen = d
      def opened = self.opened

    def withOpened(d: Door): State = new State:
      def prize = self.prize
      def chosen = self.chosen
      def opened = d

  object State:
    given Eq[State] = Eq.instance { (s1, s2) =>
      s1.chosen === s2.chosen && s1.opened === s2.opened && s1.prize === s2.prize
    }

  // start :: State
  // start = Doors {prize=u,chosen=u,opened=u} where u=undefined
  val start: State = new State:
    def prize = ???
    def chosen = ???
    def opened = ???

  // hide :: Trans State
  // hide s = uniform [s{prize=d} | d <- doors]
  val hide: Transition[State] = s =>
    uniform {
      doors.map(s.withPrize)
    }

  // choose :: Trans State
  // choose s = uniform [s{chosen=d} | d <- doors]
  val choose: Transition[State] = s =>
    uniform {
      doors.map(s.withChosen)
    }

  // open :: Trans State
  // open s = uniform [s{opened=d} | d <- doors \\ [prize s,chosen s]]
  val open: Transition[State] = s =>
    uniform {
      doors
        .filterNot { d =>
          d === s.prize || d === s.chosen
        }
        .map { d =>
          s.withOpened(d)
        }
    }

  // type Strategy = Trans State
  type Strategy = Transition[State]

  // switch :: Strategy
  // switch s = uniform [s{chosen=d} | d <- doors \\ [chosen s,opened s]]
  val switch: Strategy = s =>
    uniform {
      doors
        .filterNot { d =>
          d === s.chosen || d === s.opened
        }
        .map { d =>
          s.withChosen(d)
        }
    }

  // certainlyT :: (a -> a) -> Trans a
  // certainlyT f = certainly . f
  def certainlyT[A](f: A => A): Transition[A] = a => certainly(a)

  // stay :: Strategy
  // stay = certainlyT id
  val stay: Strategy = certainlyT(identity)

  // game :: Strategy -> Trans State
  // game s = sequ [hide,choose,open,s]
  def game(strategy: Strategy): Transition[State] = state =>
    for
      x1 <- hide(state)
      x2 <- choose(x1)
      x3 <- open(x2)
      result <- strategy(x3)
    yield result

  // result :: State -> Outcome
  // result s = if chosen s==prize s then Win else Lose
  def result(s: State): Outcome =
    if s.chosen === s.prize then Outcome.Win else Outcome.Lose

  // eval :: Strategy -> Dist Outcome
  // eval s = mapD result (game s start)

  def eval(strategy: Strategy): Dist[Outcome] =
    game(strategy)(start).map(result)

  // simEval :: Int -> Strategy -> RDist Outcome
  // simEval k s = mapD result ‘fmap‘ (k ~. game s) start
  def simEval(n: Int)(strategy: Strategy)(using sim: Sim[Dist]): IO[Dist[Outcome]] =
    val x: IO[Dist[State]] = sim.iterate(n)(game(strategy))(start)
    x.map(_.map(result))

  override def run: IO[Unit] = for
    _ <- IO.println(eval(stay).normalise)
    _ <- IO.println(eval(switch).normalise)

    _ <- IO.println("running sim ...")
    given Random[IO] <- Random.scalaUtilRandom[IO]
    given Sim[Dist] = Sim.given_Sim_Dist

    n = 1000
    evalStay <- simEval(n)(stay).map(_.normalise)
    evalSwitch <- simEval(n)(switch).map(_.normalise)
    _ <- IO.println(evalStay)
    _ <- IO.println(evalSwitch)
  yield ()
