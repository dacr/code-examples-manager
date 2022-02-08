package fr.janalyse.cem

import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zio.*
import zio.logging.LogFormat
import zio.logging.backend.SLF4J

object Main extends ZIOAppDefault {

  val aspect: RuntimeConfigAspect =
    SLF4J.slf4j(
      LogLevel.Debug,
      format = LogFormat.colored
    )

  override def hook = aspect

  val configLayer     = ZLayer.fromZIO(Configuration())
  val httpClientLayer = AsyncHttpClientZioBackend.layer()

  override def run = {
    Synchronize.synchronizeEffect.provide(System.live, Console.live, Clock.live, configLayer, httpClientLayer)
  }
}
