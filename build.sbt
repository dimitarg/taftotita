ThisBuild / organization := "io.github.dimitarg"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.4"

val testcontainersScalaVersion = "0.41.4"
val ironVersion = "2.6.0"
val http4sVersion = "0.23.30"
val tapirVersion = "1.11.10"
val monocleVersion = "3.3.0"
val natchezVersion = "0.3.5"

def module(name: String): Project = Project(id = s"tafto-${name}",  base = file(s"modules/$name"))
  .settings(
    libraryDependencies ++= Seq(
      "io.github.dimitarg"  %%  "weaver-test-extra" % "0.5.11" % "test",
      "org.scalacheck" %% "scalacheck" % "1.18.1",
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
      "com.github.cb372" %% "cats-retry" % "3.1.3",
      "dev.optics" %% "monocle-core"  % monocleVersion,
      "dev.optics" %% "monocle-macro" % monocleVersion,
      "org.tpolecat" %% "natchez-core" % natchezVersion,
      "org.tpolecat" %% "natchez-noop" % natchezVersion,
      "org.tpolecat" %% "natchez-mtl" % natchezVersion,
      "org.tpolecat" %% "natchez-honeycomb" % natchezVersion,
    )
  )

lazy val config = module("config")
  .dependsOn(core)

lazy val migrations = module("migrations")
  .dependsOn(config)
  .settings(
    libraryDependencies ++= Seq(
      "org.flywaydb" % "flyway-database-postgresql" % "11.1.0",
      "org.postgresql" % "postgresql" % "42.7.4"
    )
  )

lazy val persist = module("persist")
  .dependsOn(
    config,
    core % "compile->compile;test->test",
    testContainers % "test",
    migrations % "test",
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
  .dependsOn(migrations, persist % "compile->compile;test->test", restServer, crypto, testContainers)

lazy val loadTests = module("load-tests")
  .dependsOn(migrations, persist, testContainers)
  .settings(
    javaOptions ++= Seq(
      "-Xms8g"
    )
  )

// make sure any test projects spinning up docker containers run in sequence, so resource usage stays sane.
(integrationTests / test) := ((integrationTests / Test / test) dependsOn (persist / Test / test)).value


lazy val root = (project in file("."))
  .aggregate(logging, core, migrations, config, persist, testContainers, restApi, restServer, localDev, crypto, integrationTests, loadTests)

