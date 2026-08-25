ThisBuild / scalaVersion := "2.12.17"
ThisBuild / version := "0.1.0"
ThisBuild / organization := "ai.native.computer.system"

lazy val obj02 = ProjectRef(file("../../objective02-riscv-core"), "root")

lazy val root = (project in file("."))
  .dependsOn(obj02)
  .settings(
    name := "an32-rtl-verification",
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3" % "3.6.0",
      "edu.berkeley.cs" %% "chiseltest" % "0.6.0",
      "org.scalatest" %% "scalatest" % "3.2.16",
      compilerPlugin("edu.berkeley.cs" % "chisel3-plugin_2.12.17" % "3.6.0")
    ),
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-language:reflectiveCalls"),
    Compile / run / fork := true
  )
