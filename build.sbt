ThisBuild / organization := "io.github.dimitarg"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.4"

val testcontainersScalaVersion = "0.41.4"

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
      "io.github.iltotore" %% "iron" % "2.6.0",
      "io.github.iltotore" %% "iron-cats" % "2.6.0",
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
      "org.tpolecat" %% "skunk-core" % "0.6.4"
    )
  )

lazy val testContainers = module("testcontainers")
  .dependsOn(config)
  .settings(
    libraryDependencies ++= Seq(
      "com.dimafeng" %% "testcontainers-scala-postgresql" % testcontainersScalaVersion
    )
  )

lazy val root = (project in file("."))
  .aggregate(core, migrations, config, persist, testContainers)

