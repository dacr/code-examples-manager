package fr.janalyse.cem

import scala.util.Properties._
import better.files._
import com.typesafe.config.{Config, ConfigFactory}
import zio.IO
import zio.config._
import zio.config.typesafe._
import zio.config.magnolia.DeriveConfigDescriptor._

case class ExamplesConfig(
  searchRootDirectories: String,
  searchGlob: String,
)

case class RenameRuleConfig(
  from: String,
  to: String,
) {
  def rename(input: String): String = {
    if (input.matches(from)) {
      input.replaceAll(from, to)
    } else input
  }
}

case class PublishAdapterConfig(
  enabled: Boolean,
  kind: String,
  activationKeyword: String,
  apiEndPoint: String,
  overviewUUID: String,
  token: Option[String],
  defaultVisibility: Option[String],
  filenameRenameRules: Map[String, RenameRuleConfig],
)

// Automatically populated by the build process from a generated config file
case class MetaConfig(
  projectName: Option[String],
  projectGroup: Option[String],
  projectPage: Option[String],
  projectCode: Option[String],
  buildVersion: Option[String],
  buildDateTime: Option[String],
  buildUUID: Option[String],
) {
  def name: String = projectName.getOrElse("code-examples-manager")

  def code: String = projectName.getOrElse("cem")

  def version: String = buildVersion.getOrElse("x.y.z")

  def dateTime: String = buildDateTime.getOrElse("?")

  def uuid: String = buildUUID.getOrElse("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

  def projectURL: String = projectPage.getOrElse("https://github.com/dacr")
}

case class CodeExampleManagerConfig(
  examples: ExamplesConfig,
  publishAdapters: Map[String, PublishAdapterConfig],
  metaInfo: MetaConfig
)

case class ApplicationConfig(
  codeExamplesManagerConfig: CodeExampleManagerConfig
)

object ApplicationConfig {
  def apply(): IO[Throwable, ApplicationConfig] = {
    val metaConfigResourceName = "cem-meta.conf"
    val automaticConfigDescriptor = descriptor[ApplicationConfig].mapKey(toKebabCase)

    def buildTypeSafeConfig: Config = {
      val customConfigFileOption = envOrNone("CEM_CONFIG_FILE").orElse(propOrNone("CEM_CONFIG_FILE"))
      ConfigFactory
        .empty()
        .withFallback(
          customConfigFileOption
            .map(f => ConfigFactory.parseFile(new java.io.File(f)))
            .getOrElse(ConfigFactory.load()))
        .withFallback(ConfigFactory.load(metaConfigResourceName))
    }

    for {
      typesafeConfig <- IO(buildTypeSafeConfig)
      configSource <- IO.fromEither(TypesafeConfigSource.fromTypesafeConfig(typesafeConfig))
      config <- IO.fromEither(zio.config.read(automaticConfigDescriptor from configSource))
    } yield config
  }
}