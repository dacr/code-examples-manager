organization := "fr.janalyse"
name         := "code-examples-manager"
homepage     := Some(new URL("https://github.com/dacr/code-examples-manager"))

licenses += "NON-AI-APACHE2" -> url(s"https://github.com/non-ai-licenses/non-ai-licenses/blob/main/NON-AI-APACHE2")

scmInfo := Some(
  ScmInfo(
    url(s"https://github.com/dacr/code-examples-manager.git"),
    s"git@github.com:dacr/code-examples-manager.git"
  )
)

scalaVersion := "3.5.1"

scalacOptions += "-Xkind-projector:underscores"

mainClass := Some("fr.janalyse.cem.Main")

lazy val versions = new {
  val sttp        = "3.9.8"
  val zio         = "2.1.9"
  val zionio      = "2.0.2"
  val zioproc     = "0.7.2"
  val zioconfig   = "4.0.2"
  val ziologging  = "2.3.1"
  val ziolmdb     = "1.8.1"
  val naturalsort = "1.0.4"
  val jgit        = "7.0.0.202409031743-r"
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

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

enablePlugins(SbtTwirl)

// TODO - to remove when twirl will be available for scala3
//libraryDependencies := libraryDependencies.value.map {
//  case module if module.name == "twirl-api" => module.cross(CrossVersion.for3Use2_13)
//  case module                               => module
//}

TwirlKeys.templateImports += "fr.janalyse.cem.model._"

// ZIO-LMDB requires special authorization at JVM level
ThisBuild / fork := true
ThisBuild / javaOptions ++= Seq("--add-opens", "java.base/java.nio=ALL-UNNAMED", "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED")
