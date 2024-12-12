package tafto.testcontainers

import tafto.util.safeAssert
import cats.implicits.*
import cats.effect.*
import fs2.io.file.*

final case class Containers(
    postgres: Postgres
)

object Containers:
  def make(config: ContainersConfig): Resource[IO, Containers] = for
    pgBind <- Resource.eval(config.pgCache.traverse(prepare))
    pg <- Postgres.make(pgBind, config.tailContainerLog)
  yield Containers(pg)

  def prepare(bind: FsBind): IO[ValidHostFsBind] = for
    home <- Files[IO].userHome
    _ <- safeAssert[IO](!bind.hostPath.isAbsolute, s"Expected relative path, got ${bind.hostPath}")
    hostPath = home / bind.hostPath
    alreadyExists <- Files[IO].exists(hostPath)
    _ <- Files[IO].createDirectories(hostPath)
  yield ValidHostFsBind(hostPath = hostPath, containerPath = bind.containerPath, isNewlyCreated = !alreadyExists)
