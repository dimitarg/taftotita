package tafto.db

import cats.effect.*
import io.github.iltotore.iron.*
import org.flywaydb.core.Flyway
import tafto.config.*
import tafto.util.*

object DatabaseMigrator:

  def migrate[F[_]: Sync](config: DatabaseConfig): F[Unit] = Sync[F].blocking {
    val flyway = Flyway
      .configure()
      .dataSource(config.jdbcUrl, config.userName.value, config.password.value.value)
      .load()
    val _ = flyway.migrate()
  }
