name := "node-examples-manager"

libraryDependencies ++= Seq(
  "org.scalatest"           %% "scalatest"                   % "3.0.8",
  "ch.qos.logback"           % "logback-classic"             % "1.2.3",
  "com.softwaremill.sttp"   %% "core"                        % "1.6.4",
  "com.softwaremill.sttp"   %% "json4s"                      % "1.6.4",
  //   "com.softwaremill.sttp"   %% "akka-http-backend"           % "1.5.17",
  "com.softwaremill.sttp"   %% "okhttp-backend"              % "1.6.4",
  "org.json4s"              %% "json4s-native"               % "3.6.7",
  "org.json4s"              %% "json4s-ext"                  % "3.6.7",
  "org.json4s"              %% "json4s-jackson"              % "3.6.7",
  "com.github.pathikrit"    %% "better-files"                % "3.8.0",
)

// Mandatory for cats and so for doodle !
scalacOptions += "-Ypartial-unification"
