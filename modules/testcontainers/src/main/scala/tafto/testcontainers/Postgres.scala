package tafto.testcontainers

import com.dimafeng.testcontainers.PostgreSQLContainer
import cats.implicits.*
import cats.effect.*
import tafto.config.*
import ciris.Secret

final case class Postgres(
    private val underlying: PostgreSQLContainer,
    databaseConfig: DatabaseConfig
)

object Postgres:
  def make: Resource[IO, Postgres] =
    Resource
      .fromAutoCloseable {
        IO.blocking {
          val container = PostgreSQLContainer()
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
