name := "rx-cats"

version := "0.1"

scalaVersion := "2.12.1"

crossScalaVersions := Seq("2.10.6", "2.11.8", "2.12.1")

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % "0.9.0",
  "io.reactivex.rxjava2" % "rxjava" % "2.0.6",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test"
)

sourceGenerators in Compile <+= Template
  .generate(
    0 -> Seq(Interfaces.callable, Interfaces.action),
    1 -> Seq(Interfaces.predicate, Interfaces.function, Interfaces.consumer),
    2 -> Seq(Interfaces.bifunction, Interfaces.biPredicate)
  )

doctestTestFramework := DoctestTestFramework.ScalaTest

