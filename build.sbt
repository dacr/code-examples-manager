organization := "fr.janalyse"
name := "code-examples-manager"
homepage := Some(new URL("https://github.com/dacr/code-examples-manager"))
licenses += "Apache 2" -> url(s"https://www.apache.org/licenses/LICENSE-2.0.txt")
scmInfo := Some(ScmInfo(url(s"https://github.com/dacr/code-examples-manager.git"), s"git@github.com:dacr/code-examples-manager.git"))

scalaVersion := "3.0.0"
//scalaVersion := "2.13.5"
scalacOptions ++= Seq()

mainClass := Some("fr.janalyse.cem.Synchronize")

lazy val versions = new {
  val sttp = "3.3.3"
  val json4s = "3.6.11"
  val betterfiles = "3.9.1"
  val yamusca = "0.8.1"
  val naturalsort = "1.0.0"
}

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % "1.0.8",
  "dev.zio" %% "zio-test" % "1.0.8",
  "dev.zio" %% "zio-test-junit" % "1.0.8",
  "dev.zio" %% "zio-logging" % "0.5.8" cross CrossVersion.for3Use2_13,
  "dev.zio" %% "zio-config" % "1.0.5" cross CrossVersion.for3Use2_13,
  "dev.zio" %% "zio-config-magnolia" % "1.0.5" cross CrossVersion.for3Use2_13,
  "dev.zio" %% "zio-config-typesafe" % "1.0.5" cross CrossVersion.for3Use2_13,

  "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % versions.sttp cross CrossVersion.for3Use2_13,
  "com.softwaremill.sttp.client3" %% "json4s" % versions.sttp cross CrossVersion.for3Use2_13,
  "com.softwaremill.sttp.client3" %% "zio-json" % versions.sttp cross CrossVersion.for3Use2_13,

  "org.json4s" %% "json4s-jackson" % versions.json4s cross CrossVersion.for3Use2_13,
  "org.json4s" %% "json4s-ext" % versions.json4s cross CrossVersion.for3Use2_13,

  "com.github.pathikrit" %% "better-files" % versions.betterfiles cross CrossVersion.for3Use2_13,
  "com.github.eikek" %% "yamusca-core" % versions.yamusca cross CrossVersion.for3Use2_13,

  "fr.janalyse" %% "naturalsort" % versions.naturalsort cross CrossVersion.for3Use2_13,
)
