package fr.janalyse.cem

import zio.*
import zio.config.*
import zio.logging.LogFormat
import zio.logging.backend.SLF4J
import sttp.client3.asynchttpclient.zio.{AsyncHttpClientZioBackend, SttpClient}

object Main extends ZIOAppDefault:

  val aspect: RuntimeConfigAspect = SLF4J.slf4j(
    LogLevel.Debug,
    format = LogFormat.colored
  )

  override def hook = aspect

  val configLayer     = ZLayer.fromZIO(Configuration())
  val httpClientLayer = AsyncHttpClientZioBackend.layer()


  val versionEffect =
    for {
      metaInfo  <- getConfig[ApplicationConfig].map(_.codeExamplesManagerConfig.metaInfo)
      version    = metaInfo.version
      appName    = metaInfo.name
      appCode    = metaInfo.code
      projectURL = metaInfo.projectURL
      _         <- Console.printLine(s"$appCode version $version")
      _         <- Console.printLine(s"$appCode project page $projectURL")
      _         <- Console.printLine(s"$appCode contact email = ${metaInfo.contactEmail}")
      _         <- Console.printLine(s"$appCode build Version = ${metaInfo.buildVersion}")
      _         <- Console.printLine(s"$appCode build DateTime = ${metaInfo.buildDateTime}")
      _         <- Console.printLine(s"$appCode build UUID = ${metaInfo.buildUUID}")
    }  yield ()


  override def run = getArgs
    .flatMap(args =>
      args.toList match {
        case "run" :: keywords =>
          Execute
            .executeEffect(keywords.toSet)
            .provideCustom(configLayer, FileSystemService.live)
            .unit

        case "version":: _ =>
          versionEffect
            .provideCustom(configLayer)
            .unit

        case "publish"::_ | _ =>
          Synchronize
            .synchronizeEffect
            .provideCustom(configLayer, httpClientLayer, FileSystemService.live)
            .unit
      }
    )
