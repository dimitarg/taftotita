ThisBuild / organization := "io.github.dimitarg"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.4"

val testcontainersScalaVersion = "0.41.4"
val ironVersion = "2.6.0"
val http4sVersion = "0.23.29"
val tapirVersion = "1.11.8"

def module(name: String): Project = Project(id = s"tafto-${name}",  base = file(s"modules/$name"))
  .settings(
    libraryDependencies ++= Seq(
      "io.github.dimitarg"  %%  "weaver-test-extra" % "0.5.11" % "test"
    )
  )

lazy val logging = module("logging")
  .settings(
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-io" % "3.11.0",
      "dev.scalafreaks" %% "odin-core" % "0.15.0",
      "dev.scalafreaks" %% "odin-slf4j-provider" % "0.15.0"
    )
  )

lazy val core = module("core")
  .dependsOn(logging)
  .settings(
    libraryDependencies ++= Seq(
      "io.github.iltotore" %% "iron" % ironVersion,
      "io.github.iltotore" %% "iron-cats" % ironVersion,
      // we depend on ciris in core because domain data types reuse `Secret` datatype.
      "is.cir" %% "ciris" % "3.6.0",
    )
  )

lazy val config = module("config")
  .dependsOn(core)

lazy val migrations = module("migrations")
  .dependsOn(config)
  .settings(
    libraryDependencies ++= Seq(
      "org.flywaydb" % "flyway-database-postgresql" % "10.20.1",
      "org.postgresql" % "postgresql" % "42.7.4"
    )
  )

lazy val persist = module("persist")
  .dependsOn(
    config,
    testContainers % "test",
    migrations % "test"
  )
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "skunk-core" % "0.6.4",
      "io.github.iltotore" %% "iron-skunk" % ironVersion,
    )
  )

lazy val restApi = module("rest-api")
  .dependsOn(core)
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % tapirVersion,
      "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.11.3",
      "io.github.iltotore" %% "iron-circe" % ironVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-iron" % tapirVersion
    )
  )

lazy val restServer = module("rest-server")
  .dependsOn(restApi)
  .settings(
    libraryDependencies ++= Seq(
        "org.http4s" %% "http4s-ember-server" % http4sVersion,
        "org.http4s" %% "http4s-dsl"          % http4sVersion,
        "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % tapirVersion
    )
  )



lazy val testContainers = module("testcontainers")
  .dependsOn(config)
  .settings(
    libraryDependencies ++= Seq(
      "com.dimafeng" %% "testcontainers-scala-postgresql" % testcontainersScalaVersion
    )
  )

lazy val localDev = module("local-dev")
  .dependsOn(migrations, persist, restServer, testContainers)

lazy val root = (project in file("."))
  .aggregate(logging, core, migrations, config, persist, testContainers, restApi, restServer, localDev)

