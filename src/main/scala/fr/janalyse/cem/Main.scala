package fr.janalyse.cem

import zio.*
import zio.config.*
import zio.logging.{LogFormat, removeDefaultLoggers}
import zio.logging.backend.SLF4J
import sttp.client3.asynchttpclient.zio.{AsyncHttpClientZioBackend, SttpClient}

object Main extends ZIOAppDefault:

  override val bootstrap = SLF4J.slf4j(
    LogLevel.Debug,
    format = LogFormat.colored
  )

  val configLayer     = ZLayer.fromZIO(Configuration())
  val httpClientLayer = AsyncHttpClientZioBackend.layer()


  override def run =
    getArgs
      .flatMap(args =>
      args.toList match {
        case "run" :: keywords =>
          Execute
            .executeEffect(keywords.toSet)
            .flatMap(status => ZIO.cond(status.forall(_.success), "All examples are successful", "Some examples has failed"))
            .flatMap(message => ZIO.log(message))
            .provide(configLayer, FileSystemService.live)
            .unit

        case "version":: _ =>
          Synchronize
            .versionEffect
            .flatMap(versionInfo => Console.printLine(versionInfo))
            .provide(configLayer)
            .unit

        case "stats":: _ =>
          Synchronize
            .examplesCollect
            .flatMap(examples => Synchronize.statsEffect(examples))
            .flatMap(statsInfo => Console.printLine(statsInfo))
            .provide(configLayer, FileSystemService.live)
            .unit

        case "publish"::_ | _ =>
          Synchronize
            .synchronizeEffect
            .provide(configLayer, httpClientLayer, FileSystemService.live, removeDefaultLoggers)
            .unit
      }
    )
