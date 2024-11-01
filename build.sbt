ThisBuild / organization := "io.github.dimitarg"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.4"

def module(name: String): Project = Project(id = name,  base = file(s"modules/$name"))

lazy val core = module("core")
  .settings(
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-io" % "3.11.0",
      "io.github.iltotore" %% "iron" % "2.6.0",
      "io.github.dimitarg"  %%  "weaver-test-extra" % "0.5.11" % "test"
    )
  )

lazy val config = module("config")
  .dependsOn(core)
  .settings(libraryDependencies ++= Seq(
    "is.cir" %% "ciris" % "3.6.0"
  ))

lazy val migrations = module("migrations")
  .dependsOn(config)
  .settings(
    libraryDependencies ++= Seq(
      "org.flywaydb" % "flyway-database-postgresql" % "10.20.1",
      "org.postgresql" % "postgresql" % "42.7.4"
    )
  )

lazy val root = (project in file("."))
  .aggregate(core, migrations, config)

