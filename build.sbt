organization := "fr.janalyse"
name := "code-examples-manager"
version := "1.0.0-SNAPSHOT"

scalaVersion := "2.13.1"

libraryDependencies ++= Seq(
  "org.scalatest"           %% "scalatest"                   % "3.1.0",
  "ch.qos.logback"           % "logback-classic"             % "1.2.3",
  "com.softwaremill.sttp"   %% "core"                        % "1.7.2",
  "com.softwaremill.sttp"   %% "json4s"                      % "1.7.2",
  "com.softwaremill.sttp"   %% "okhttp-backend"              % "1.7.2",
  "org.json4s"              %% "json4s-native"               % "3.6.7",
  "org.json4s"              %% "json4s-ext"                  % "3.6.7",
  "org.json4s"              %% "json4s-jackson"              % "3.6.7",
  "com.github.pathikrit"    %% "better-files"                % "3.8.0"
)

