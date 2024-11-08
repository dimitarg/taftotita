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

final case class Postgres(
    private val underlying: PostgreSQLContainer,
    databaseConfig: DatabaseConfig
)

object Postgres:
  def make(dataBind: Option[ValidHostFsBind]): Resource[IO, Postgres] =
    Resource
      .fromAutoCloseable {
        IO.blocking {
          val container = PostgreSQLContainer()
          container.configure { c =>
            dataBind.foreach { bind =>
              c.addFileSystemBind(bind.hostPath.toString, bind.containerPath.toString, BindMode.READ_WRITE)

              // the default strategy waits for the string "database system is ready to accept connections" to be logged TWICE.
              // this is only true when the PG system is initialising the first time, but not on subsequent attempts, when data already exists.
              c.withLogConsumer { frame =>
                print(frame.getUtf8String())
              }
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
