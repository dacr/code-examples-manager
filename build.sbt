organization := "fr.janalyse"
name         := "code-examples-manager"
homepage     := Some(new URL("https://github.com/dacr/code-examples-manager"))

licenses += "Apache 2" -> url(s"https://www.apache.org/licenses/LICENSE-2.0.txt")

scmInfo := Some(
  ScmInfo(
    url(s"https://github.com/dacr/code-examples-manager.git"),
    s"git@github.com:dacr/code-examples-manager.git"
  )
)

scalaVersion := "3.3.0"

mainClass := Some("fr.janalyse.cem.Main")

lazy val versions = new {
  val sttp        = "3.8.15"
  val zio         = "2.0.14"
  val zionio      = "2.0.1"
  val zioproc     = "0.7.2"
  val zioconfig   = "4.0.0-RC16"
  val ziologging  = "2.1.13"
  val ziolmdb     = "1.1.0"
  val naturalsort = "1.0.2"
  val jgit        = "6.5.0.202303070854-r"
  // val logback     = "1.4.7"
  val log4j2      = "2.20.0"
}

libraryDependencies ++= Seq(
  "dev.zio"                       %% "zio"                           % versions.zio,
  "dev.zio"                       %% "zio-test"                      % versions.zio % Test,
  "dev.zio"                       %% "zio-test-junit"                % versions.zio % Test,
  "dev.zio"                       %% "zio-test-sbt"                  % versions.zio % Test,
  "dev.zio"                       %% "zio-test-scalacheck"           % versions.zio % Test,
  "dev.zio"                       %% "zio-streams"                   % versions.zio,
  "dev.zio"                       %% "zio-nio"                       % versions.zionio,
  "dev.zio"                       %% "zio-process"                   % versions.zioproc,
  "dev.zio"                       %% "zio-logging"                   % versions.ziologging,
  "dev.zio"                       %% "zio-logging-slf4j-bridge"      % versions.ziologging,
  "dev.zio"                       %% "zio-config"                    % versions.zioconfig,
  "dev.zio"                       %% "zio-config-typesafe"           % versions.zioconfig,
  "dev.zio"                       %% "zio-config-magnolia"           % versions.zioconfig,
  "fr.janalyse"                   %% "zio-lmdb"                      % versions.ziolmdb,
  "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % versions.sttp,
  "com.softwaremill.sttp.client3" %% "zio-json"                      % versions.sttp,
  "fr.janalyse"                   %% "naturalsort"                   % versions.naturalsort,
  "org.eclipse.jgit"               % "org.eclipse.jgit"              % versions.jgit
)

//excludeDependencies += "org.scala-lang.modules" % "scala-collection-compat_2.13"

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

enablePlugins(SbtTwirl)

// TODO - to remove when twirl will be available for scala3
libraryDependencies := libraryDependencies.value.map {
  case module if module.name == "twirl-api" => module.cross(CrossVersion.for3Use2_13)
  case module                               => module
}

TwirlKeys.templateImports += "fr.janalyse.cem.model._"

// ZIO-LMDB requires special authorization at JVM level
ThisBuild / fork := true
ThisBuild / javaOptions ++= Seq("--add-opens", "java.base/java.nio=ALL-UNNAMED", "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED")
