package tafto.prob

type Event[A] = A => Boolean

// a probabilistic function where source and target are the same
type Transition[A] = A => Dist[A]
