package tafto.testcontainers

import cats.implicits.*
import fs2.io.file.Path
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.Positive

final case class FsBind(
    hostPath: Path,
    containerPath: Path
)

final case class ValidHostFsBind(
    hostPath: Path,
    containerPath: Path,
    isNewlyCreated: Boolean
)

object FsBind:
  val forPgDocker: FsBind = FsBind(
    hostPath = Path(".tafto/data/pg"),
    containerPath = Path("/var/lib/postgresql/data")
  )

final case class ContainersConfig(
    pgCache: Option[FsBind],
    tailContainerLog: Boolean,
    reenableFsync: Boolean,
    poolSize: Int :| Positive
)

object ContainersConfig:
  val test = ContainersConfig(pgCache = None, tailContainerLog = false, reenableFsync = false, poolSize = 10)
  val localDev =
    ContainersConfig(pgCache = FsBind.forPgDocker.some, tailContainerLog = true, reenableFsync = true, poolSize = 10)
  def loadTest(poolSize: Int :| Positive) =
    ContainersConfig(pgCache = None, tailContainerLog = true, reenableFsync = true, poolSize)
