package tafto.testcontainers

import java.time.Duration
import java.time.temporal.ChronoUnit

import com.dimafeng.testcontainers.PostgreSQLContainer
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import cats.implicits.*
import cats.effect.*
import tafto.config.*
import ciris.Secret
import io.odin.*
import cats.effect.unsafe.implicits.global
import org.testcontainers.utility.DockerImageName

final case class Postgres(
    private val underlying: PostgreSQLContainer,
    databaseConfig: DatabaseConfig
)

object Postgres:

  val imageName = DockerImageName.parse("postgres:17.1").asCompatibleSubstituteFor("postgres")

  def make(dataBind: Option[ValidHostFsBind], tailLog: Boolean): Resource[IO, Postgres] =
    Resource
      .fromAutoCloseable {
        IO.blocking {
          val container = PostgreSQLContainer(dockerImageNameOverride = imageName)
          container.configure { c =>

            if (tailLog) {
              val logger = consoleLogger[IO]()
              val _ = c.withLogConsumer { frame =>
                val logMsg = frame.getUtf8StringWithoutLineEnding().trim()
                if (logMsg.nonEmpty) {
                  logger.info(logMsg).unsafeRunSync()
                }
              }
            }

            dataBind.foreach { bind =>
              c.addFileSystemBind(bind.hostPath.toString, bind.containerPath.toString, BindMode.READ_WRITE)
              // the default strategy waits for the string "database system is ready to accept connections" to be logged TWICE.
              // this is only true when the PG system is initialising the first time, but not on subsequent attempts, when data already exists.
              if (!bind.isNewlyCreated) {
                c.setWaitStrategy(
                  new LogMessageWaitStrategy()
                    .withRegEx(".*database system is ready to accept connections.*\\s")
                    .withTimes(1)
                    .withStartupTimeout(Duration.of(60, ChronoUnit.SECONDS))
                )
              }
            }
          }
          container.start()
          container
        }
      }
      .evalMap { container =>
        val config = for {
          host <- container.refineHost
          port <- container.refinePort(org.testcontainers.containers.PostgreSQLContainer.POSTGRESQL_PORT)
          dbName <- DatabaseName.either(PostgreSQLContainer.defaultDatabaseName)
          user <- UserName.either(PostgreSQLContainer.defaultUsername)
          pass = Password(Secret(PostgreSQLContainer.defaultPassword))
        } yield DatabaseConfig(dbName, host, port, user, pass)

        config.asIO.map { config =>
          Postgres(container, config)
        }
      }
