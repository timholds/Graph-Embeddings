name := "scaling-science-2"

version := "0.1"

scalaVersion := "2.12.6"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked",
  "-Ypartial-unification",
  "-language:existentials",
  "-language:higherKinds"
)

libraryDependencies += "io.circe" %% "circe-generic" % "0.9.3"
libraryDependencies += "io.circe" %% "circe-fs2" % "0.9.0"

libraryDependencies += "co.fs2" %% "fs2-core" % "0.10.4" // For cats 1.1.0 and cats-effect 0.10
libraryDependencies += "co.fs2" %% "fs2-io" % "0.10.4" // io library