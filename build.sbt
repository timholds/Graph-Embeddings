name := "scaling-science"

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


libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % "1.0.1",
  "co.fs2" %% "fs2-io" % "0.10.4",
  "co.fs2" %% "fs2-core" % "0.10.4",
  "io.circe" %% "circe-generic" % "0.9.3",
  "io.circe" %% "circe-fs2" % "0.9.0",
  "org.janusgraph" % "janusgraph-hbase" % "0.2.0",
  "org.apache.hbase" % "hbase-shaded-client" % "2.0.0",
  "com.michaelpollmeier" %% "gremlin-scala" % "3.2.5.2",
  "org.apache.tinkerpop" % "gremlin-driver" % "3.2.5",
  "org.slf4j" % "slf4j-nop" % "1.7.25",
  "io.jvm.uuid" %% "scala-uuid" % "0.2.4"
)