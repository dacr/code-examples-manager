organization := "fr.janalyse"
name := "code-examples-manager"
homepage := Some(new URL("https://github.com/dacr/code-examples-manager"))
licenses += "Apache 2" -> url(s"http://www.apache.org/licenses/LICENSE-2.0.txt")
scmInfo := Some(ScmInfo(url(s"https://github.com/dacr/code-examples-manager.git"), s"git@github.com:dacr/code-examples-manager.git"))

scalaVersion := "2.13.3"
scalacOptions ++= Seq( "-deprecation", "-unchecked", "-feature")

lazy val versions = new {
  val scalatest = "3.2.0"
  val logback = "1.2.3"
  val sttp = "2.2.1"
  val json4s = "3.6.9"
  val betterfiles = "3.9.1"
}

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % versions.scalatest,
  "ch.qos.logback" % "logback-classic" % versions.logback,
  "com.softwaremill.sttp.client" %% "core" % versions.sttp,
  "com.softwaremill.sttp.client" %% "json4s" % versions.sttp,
  "com.softwaremill.sttp.client" %% "okhttp-backend" % versions.sttp,
  "org.json4s" %% "json4s-native" % versions.json4s,
  "org.json4s" %% "json4s-ext" % versions.json4s,
  "com.github.pathikrit" %% "better-files" % versions.betterfiles
)

