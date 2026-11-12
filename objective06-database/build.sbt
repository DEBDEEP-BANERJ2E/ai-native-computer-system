ThisBuild / scalaVersion := "2.12.17"
ThisBuild / version := "0.1.0"
ThisBuild / organization := "ai.native.computer.system"

lazy val obj04 = ProjectRef(file("../objective04-system"), "root")

lazy val root = (project in file("."))
  .dependsOn(obj04)
  .settings(
    name := "objective06-database",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.16" % Test
    ),
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
    Test / parallelExecution := false
  )
