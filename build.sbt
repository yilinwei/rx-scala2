lazy val catsVersion = "0.9.0"
lazy val rxVersion = "2.0.6"

lazy val generateSettings = Seq(
  sourceGenerators in Compile <+= KConvertGenerator.generate(9),
  sourceGenerators in Compile <+= ConversionGenerator
    .generate(
      0 -> Seq(Interfaces.callable, Interfaces.action),
      1 -> Seq(Interfaces.predicate, Interfaces.function, Interfaces.consumer),
      2 -> Seq(Interfaces.bifunction, Interfaces.biPredicate),
      3 -> Seq(Interfaces.function3),
      4 -> Seq(Interfaces.function4),
      5 -> Seq(Interfaces.function5),
      6 -> Seq(Interfaces.function6),
      7 -> Seq(Interfaces.function7),
      8 -> Seq(Interfaces.function8),
      9 -> Seq(Interfaces.function9)
    )
)

lazy val commonScalacOptions = Seq(
  "-encoding", "UTF-8",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps"
)

lazy val commonSettings = Seq(
  organization := "io.reactivex",
  version := "2.0.0-SNAPSHOT",
  scalaVersion := "2.12.1",
  crossScalaVersions := Seq("2.10.6", "2.11.8", "2.12.1"),
  libraryDependencies ++= Seq(
    "io.reactivex.rxjava2" % "rxjava" % rxVersion,
    "org.scalatest" %% "scalatest" % "3.0.1" % "test"
  ),
  scalacOptions ++= commonScalacOptions,
  doctestTestFramework := DoctestTestFramework.ScalaTest
)

lazy val core = (project in file("core"))
  .settings(
    name := "rxscala",
    commonSettings,
    generateSettings,
    initialCommands :=
      s"""
         import io.rx._
         import io.rx.implicits._
       """
  )

lazy val cats = (project in file("cats"))
  .settings(libraryDependencies += "org.typelevel" %% "cats-core" % catsVersion)
  .dependsOn(core)
  .settings(
    name := "rxscala-cats",
    commonSettings,
    initialCommands :=
      s"""
          import cats._
          import cats.implicits._
          import io.rx._
          import io.rx.implicits._
          import io.rx.cats.implicits._
       """
  )

lazy val root = (project in file(".")).aggregate(cats, core)