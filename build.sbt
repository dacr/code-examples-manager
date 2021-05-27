organization := "fr.janalyse"
name := "code-examples-manager"
homepage := Some(new URL("https://github.com/dacr/code-examples-manager"))
licenses += "Apache 2" -> url(s"https://www.apache.org/licenses/LICENSE-2.0.txt")
scmInfo := Some(ScmInfo(url(s"https://github.com/dacr/code-examples-manager.git"), s"git@github.com:dacr/code-examples-manager.git"))

scalaVersion := "3.0.0"

mainClass := Some("fr.janalyse.cem.Synchronize")

lazy val versions = new {
  val sttp = "3.3.5"
  val zio = "1.0.8"
  val zioconfig = "1.0.6"
  val ziologging = "0.5.10"
  val json4s = "3.6.11"
  val betterfiles = "3.9.1"
  val yamusca = "0.8.1"
  val naturalsort = "1.0.1"
  val circe = "0.14.1"
}

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % versions.zio,
  "dev.zio" %% "zio-test" % versions.zio,
  "dev.zio" %% "zio-test-junit" % versions.zio,
  "dev.zio" %% "zio-logging" % versions.ziologging,
  "dev.zio" %% "zio-config" % versions.zioconfig,
  "dev.zio" %% "zio-config-magnolia" % versions.zioconfig cross CrossVersion.for3Use2_13,
  "dev.zio" %% "zio-config-typesafe" % versions.zioconfig,

  "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % versions.sttp,
  "com.softwaremill.sttp.client3" %% "circe" % versions.sttp,
  "com.github.pathikrit" %% "better-files" % versions.betterfiles cross CrossVersion.for3Use2_13,
  "com.github.eikek" %% "yamusca-core" % versions.yamusca cross CrossVersion.for3Use2_13,

  "fr.janalyse" %% "naturalsort" % versions.naturalsort,
)
