name := "dhall-scala"
version := "0.1"
scalaVersion := "2.12.10"
crossScalaVersions := List(scalaVersion.value, "2.13.1")

libraryDependencies ++= Seq(
  "org.specs2" %% "specs2-core" % "4.8.2" % Test,
  "org.typelevel" %% "cats-core" % "2.1.0",
  "org.parboiled" %% "parboiled" % "2.1.8"
)
