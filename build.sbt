ThisBuild / organization := "io.github.dimitarg"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.4"

val testcontainersScalaVersion = "0.41.8"
val ironVersion = "2.6.0"
val http4sVersion = "0.23.30"
val tapirVersion = "1.11.14"
val monocleVersion = "3.3.0"
val circeVersion = "0.14.10"

val otel4sVersion = "0.11.2"
val openTelemetryVersion = "1.47.0"

val otelRuntime = Seq(
  "io.opentelemetry" % "opentelemetry-exporter-otlp" % openTelemetryVersion % Runtime,
  "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % openTelemetryVersion % Runtime
)

def module(name: String): Project = Project(id = s"tafto-${name}",  base = file(s"modules/$name"))
  .settings(
    libraryDependencies ++= Seq(
      "io.github.dimitarg"  %%  "weaver-test-extra" % "0.5.11" % "test",
      "org.scalacheck" %% "scalacheck" % "1.18.1"  % "test",
      "com.disneystreaming" %% "weaver-scalacheck" % "0.8.4" % "test",
    )
  )
  .settings(
    scalacOptions ++= Seq(
      "-source:future",
      // ??? for some reason unused warnings don't work inside for comprehensions, need to investigate why.
      "-Wunused:all",
    ),
    Test / fork := true,
    run / fork := true,

    // these are used by Scalafix
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
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
      "io.github.iltotore" %% "iron-scalacheck" % ironVersion  % "test",
      // we depend on ciris in core because domain data types reuse `Secret` datatype.
      "io.github.iltotore" %% "iron-ciris" % ironVersion,
      "is.cir" %% "ciris" % "3.7.0",
      "com.github.cb372" %% "cats-retry" % "4.0.0",
      "dev.optics" %% "monocle-core"  % monocleVersion,
      "dev.optics" %% "monocle-macro" % monocleVersion,
      "org.typelevel" %% "otel4s-core" % otel4sVersion,
      "org.typelevel" %% "otel4s-oteljava" % otel4sVersion,
    )
  )

lazy val config = module("config")
  .dependsOn(core)

lazy val migrations = module("migrations")
  .dependsOn(config)
  .settings(
    libraryDependencies ++= Seq(
      "org.flywaydb" % "flyway-database-postgresql" % "11.3.1",
      "org.postgresql" % "postgresql" % "42.7.5"
    )
  )

// iron-skunk depends on 0.x non-milestone, override that.
ThisBuild / libraryDependencySchemes += "org.tpolecat" %% "skunk-core" % VersionScheme.Always

lazy val persist = module("persist")
  .dependsOn(
    config,
    core % "compile->compile;test->test",
    testContainers % "test",
    migrations % "test",
  )
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "skunk-core" % "1.0.0-M10",
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
      "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.11.7",
      "io.github.iltotore" %% "iron-circe" % ironVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-iron" % tapirVersion
    )
  )

lazy val restServer = module("rest-server")
  .dependsOn(restApi)
  .settings(
    libraryDependencies ++= Seq(
        "org.http4s" %% "http4s-ember-server" % http4sVersion,
        "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % tapirVersion
    )
  )

lazy val crypto = module("crypto")
  .dependsOn(config)
  .settings(
    libraryDependencies ++= Seq(
      "io.github.jmcardon" %% "tsec-password" % "0.5.0",
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
  .dependsOn(migrations, persist, restServer, crypto, testContainers)

lazy val integrationTests = module("integration-tests")
  .dependsOn(migrations, persist % "compile->compile;test->test", restServer, crypto, testContainers, json)

lazy val loadTests = module("load-tests")
  .dependsOn(migrations, persist, testContainers, json)
  .settings(
    libraryDependencies ++= otelRuntime,
    javaOptions ++= Seq(
      "-Xms8g",
    ),
  )

lazy val json = module("json")
  .dependsOn(core % "compile->compile;test->test")
  .settings(libraryDependencies ++= Seq(
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,
    "io.github.iltotore" %% "iron-circe" % ironVersion,
  ))

// make sure any test projects spinning up docker containers run in sequence, so resource usage stays sane.
(integrationTests / test) := ((integrationTests / Test / test) dependsOn (persist / Test / test)).value


lazy val root = (project in file("."))
  .aggregate(logging, core, migrations, config, persist, testContainers, restApi, restServer, localDev, crypto, integrationTests, loadTests, json)

