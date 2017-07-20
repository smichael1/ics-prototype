import sbt.Keys._
import sbt._

import Dependencies._
import Settings._

val csw = (project in file("."))
  .settings(defaultSettings: _*)
  .settings(name := "Single Axis")
  .aggregate(singleaxis )

def compile(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")

def test(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")

def runtime(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "runtime")

def container(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "container")

// EndToEnd Example project
lazy val singleaxis = project
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("SingleAxis", "Single Axis Example", "More complicated example showing CSW features"): _*)
  .settings(libraryDependencies ++=
    compile(pkg, cs, ccs, ts, events, alarms, containerCmd, seqSupport, log) ++
      test(scalaTest, akkaTestKit)
  )
