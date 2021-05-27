organization := "fr.janalyse"
name := "code-examples-manager"
homepage := Some(new URL("https://github.com/dacr/code-examples-manager"))
licenses += "Apache 2" -> url(s"https://www.apache.org/licenses/LICENSE-2.0.txt")
scmInfo := Some(ScmInfo(url(s"https://github.com/dacr/code-examples-manager.git"), s"git@github.com:dacr/code-examples-manager.git"))

scalaVersion := "3.0.0"
scalacOptions ++= Seq()

mainClass := Some("fr.janalyse.cem.Synchronize")

lazy val versions = new {
  val sttp = "3.3.4"
  val zio = "1.0.8"
  val json4s = "3.6.11"
  val betterfiles = "3.9.1"
  val yamusca = "0.8.1"
  val naturalsort = "1.0.0"
  val circe = "0.14.0-M7"
}

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % versions.zio,
  "dev.zio" %% "zio-test" % versions.zio,
  "dev.zio" %% "zio-test-junit" % versions.zio,
  "dev.zio" %% "zio-logging" % "0.5.8" cross CrossVersion.for3Use2_13,
  "dev.zio" %% "zio-config" % "1.0.5" cross CrossVersion.for3Use2_13,
  "dev.zio" %% "zio-config-magnolia" % "1.0.5" cross CrossVersion.for3Use2_13,
  "dev.zio" %% "zio-config-typesafe" % "1.0.5" cross CrossVersion.for3Use2_13,

  "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % versions.sttp,
  "com.softwaremill.sttp.client3" %% "circe" % versions.sttp,
  "io.circe" %% "circe-generic" % versions.circe,
  "com.github.pathikrit" %% "better-files" % versions.betterfiles cross CrossVersion.for3Use2_13,
  "com.github.eikek" %% "yamusca-core" % versions.yamusca cross CrossVersion.for3Use2_13,

  "fr.janalyse" %% "naturalsort" % versions.naturalsort cross CrossVersion.for3Use2_13,
)
