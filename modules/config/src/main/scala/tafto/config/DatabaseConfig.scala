package tafto.config

import tafto.util.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

opaque type DatabaseName = NonEmptyString
object DatabaseName extends RefinedTypeOps[String, MinLength[1], DatabaseName]

final case class DatabaseConfig(
    database: DatabaseName,
    host: Host,
    port: Port,
    userName: UserName,
    password: Password
):
  def jdbcUrl: String = s"jdbc:postgresql://${host.value}/${database.value}"
