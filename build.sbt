name := "rx-cats"

version := "0.1"

scalaVersion := "2.12.1"

crossScalaVersions := Seq("2.10.6", "2.11.8", "2.12.1")

libraryDependencies += "org.typelevel" %% "cats-core" % "0.9.0"
libraryDependencies += "io.reactivex.rxjava2" % "rxjava" % "2.0.6"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"

doctestTestFramework := DoctestTestFramework.ScalaTest

