package fr.janalyse.cem

import zio.*
import zio.config.*
import zio.config.typesafe.*
import zio.config.magnolia.*

import zio.logging.{LogFormat, removeDefaultLoggers}
import zio.logging.backend.SLF4J
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zio.lmdb.{LMDB, LMDBConfig}

object Main extends ZIOAppDefault {

  private def loadTypesafeBasedConfigData(configFileOption: Option[String]) = {
    import com.typesafe.config.{Config, ConfigFactory}
    val metaDataConfig    = ConfigFactory.load("cem-meta.conf") // TODO - TO REMOVE - very old stuff
    val applicationConfig = configFileOption
      .map(f => ConfigFactory.parseFile(new java.io.File(f)))
      .getOrElse(ConfigFactory.load())
    ConfigFactory
      .empty()
      .withFallback(applicationConfig)
      .withFallback(metaDataConfig)
      .resolve()
  }

  val configProviderLogic = for {
    configFileEnvOption  <- System.env("CEM_CONFIG_FILE")
    configFilePropOption <- System.property("CEM_CONFIG_FILE")
    configFileOption      = configFileEnvOption.orElse(configFilePropOption)
    tconfig              <- ZIO.attempt(loadTypesafeBasedConfigData(configFileOption))
    configProvider        = ConfigProvider.fromTypesafeConfig(tconfig)
  } yield {
    configProvider
  }

  override val bootstrap =
    //removeDefaultLoggers ++ SLF4J.slf4j(format = LogFormat.colored) ++ ZLayer.fromZIO(configProviderLogic.map(provider => Runtime.setConfigProvider(provider))).flatten
    SLF4J.slf4j(format = LogFormat.colored) ++ ZLayer.fromZIO(configProviderLogic.map(provider => Runtime.setConfigProvider(provider))).flatten

  val httpClientLayer = AsyncHttpClientZioBackend.layer()

  override def run =
    getArgs
      .flatMap(args =>
        val chosenBehavior = args.toList match {
          case "run" :: keywords =>
            Execute
              .executeEffect(keywords.toSet)
              .flatMap(status => ZIO.cond(status.forall(_.success), "All examples are successful", "Some examples has failed"))
              .flatMap(message => ZIO.log(message))

          case "version" :: _ =>
            Synchronize.versionEffect
              .flatMap(versionInfo => Console.printLine(versionInfo))

          case "stats" :: _ =>
            Synchronize.examplesCollect
              .flatMap(examples => Synchronize.statsEffect(examples))
              .flatMap(statsInfo => Console.printLine(statsInfo))

          case "publish" :: _ | _ =>
            Synchronize.synchronizeEffect.unit
        }
        chosenBehavior.unit.provide(httpClientLayer, FileSystemService.live, LMDB.live, Scope.default)
      )
}
