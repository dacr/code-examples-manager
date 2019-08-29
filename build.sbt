name := "code-examples-manager"

scalaVersion := "2.13.0"

libraryDependencies ++= Seq(
  "org.scalatest"           %% "scalatest"                   % "3.0.8",
  "ch.qos.logback"           % "logback-classic"             % "1.2.3",
  "com.softwaremill.sttp"   %% "core"                        % "1.6.4",
  "com.softwaremill.sttp"   %% "json4s"                      % "1.6.4",
  "com.softwaremill.sttp"   %% "okhttp-backend"              % "1.6.4",
  "org.json4s"              %% "json4s-native"               % "3.6.7",
  "org.json4s"              %% "json4s-ext"                  % "3.6.7",
  "org.json4s"              %% "json4s-jackson"              % "3.6.7",
  "com.github.pathikrit"    %% "better-files"                % "3.8.0"
)

