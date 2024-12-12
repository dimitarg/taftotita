package tafto.config

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import tafto.util.*

opaque type DatabaseName = NonEmptyString
object DatabaseName extends RefinedTypeOps[String, MinLength[1], DatabaseName]

final case class DatabaseConfig(
    database: DatabaseName,
    host: Host,
    port: Port,
    userName: UserName,
    password: Password
):
  def jdbcUrl: String = s"jdbc:postgresql://${host.value}:${port.value}/${database.value}"
