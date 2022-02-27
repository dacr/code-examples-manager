package fr.janalyse.cem

import zio.*
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

  override def run = getArgs
    .flatMap(args =>
      args.toList match {
        case "run" :: keywords =>
          Execute
            .executeEffect(keywords.toSet)
            .provideCustom(configLayer, FileSystemService.live)
            .unit

        case _ =>
          Synchronize.synchronizeEffect
            .provideCustom(configLayer, httpClientLayer, FileSystemService.live)
            .unit
      }
    )
