ThisBuild / scalaVersion := "2.12.17"
ThisBuild / version := "0.1.0"
ThisBuild / organization := "ai.native.computer.system"

lazy val root = (project in file("."))
  .settings(
    name := "objective01-digital-logic",
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3" % "3.6.0",
      "edu.berkeley.cs" %% "chiseltest" % "0.6.0" % Test,
      compilerPlugin("edu.berkeley.cs" % "chisel3-plugin_2.12.17" % "3.6.0")
    ),
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-language:reflectiveCalls"),
    Test / parallelExecution := false,
    Compile / run / fork := true
  )