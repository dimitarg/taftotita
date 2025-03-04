package tafto.prob.testutil

def probEquals(allowedError: Double)(x: Double, y: Double): Boolean =
  Math.abs(x - y) <= math.abs(allowedError)

val defaultError = 0.000001

def probEquals(x: Double, y: Double): Boolean =
  probEquals(defaultError)(x, y)
