ThisBuild / scalaVersion := "2.12.17"
ThisBuild / version := "0.1.0"
ThisBuild / organization := "ai.native.computer.system"

lazy val obj01 = ProjectRef(file("../objective01-digital-logic"), "root")

lazy val root = (project in file("."))
  .dependsOn(obj01)
  .settings(
    name := "objective02-riscv-core",
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3" % "3.6.0",
      "edu.berkeley.cs" %% "chiseltest" % "0.6.0" % Test,
      "org.scalatest" %% "scalatest" % "3.2.16" % Test,
      compilerPlugin("edu.berkeley.cs" % "chisel3-plugin_2.12.17" % "3.6.0")
    ),
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-language:reflectiveCalls"),
    Test / parallelExecution := false,
    Compile / run / fork := true
  )
