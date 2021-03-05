organization := "fr.janalyse"
name := "code-examples-manager"
homepage := Some(new URL("https://github.com/dacr/code-examples-manager"))
licenses += "Apache 2" -> url(s"http://www.apache.org/licenses/LICENSE-2.0.txt")
scmInfo := Some(ScmInfo(url(s"https://github.com/dacr/code-examples-manager.git"), s"git@github.com:dacr/code-examples-manager.git"))

scalaVersion := "2.13.5"
scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-Xmacro-settings:materialize-derivations")

mainClass := Some("fr.janalyse.cem.Synchronize")

lazy val versions = new {
  val scalatest = "3.2.5"
  val logback = "1.2.3"
  val sttp = "2.2.9"
  val json4s = "3.6.11"
  val betterfiles = "3.9.1"
  val yamusca = "0.8.0"
  val pureConfig = "0.14.1"
}

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % versions.scalatest % Test,
  "ch.qos.logback" % "logback-classic" % versions.logback,
  "com.softwaremill.sttp.client" %% "core" % versions.sttp,
  "com.softwaremill.sttp.client" %% "json4s" % versions.sttp,
  "com.softwaremill.sttp.client" %% "okhttp-backend" % versions.sttp,
  "org.json4s" %% "json4s-jackson" % versions.json4s,
  "org.json4s" %% "json4s-ext" % versions.json4s,
  "com.github.pathikrit" %% "better-files" % versions.betterfiles,
  "com.github.eikek" %% "yamusca-core" % versions.yamusca,
  "com.github.pureconfig" %% "pureconfig" % versions.pureConfig,
)
