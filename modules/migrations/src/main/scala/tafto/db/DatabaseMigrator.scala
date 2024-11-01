package tafto.db

import org.flywaydb.core.Flyway

import cats.effect.*
import io.github.iltotore.iron.*
import tafto.config.*
import tafto.util.*
import ciris.Secret

object DatabaseMigrator extends IOApp.Simple:

  override def run: IO[Unit] =
    val databaseConfig = DatabaseConfig(
      database = DatabaseName("postgres"),
      host = Host("localhost"),
      port = Port(5432),
      userName = UserName("postgres"),
      password = Password(Secret("postgres"))
    )
    migrate[IO](databaseConfig)

  def migrate[F[_]: Sync](config: DatabaseConfig): F[Unit] = Sync[F].blocking {
    val flyway = Flyway
      .configure()
      .dataSource(config.jdbcUrl, config.userName.value, config.password.value.value)
      .load()
    val _ = flyway.migrate()
  }
