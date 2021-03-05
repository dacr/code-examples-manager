package fr.janalyse.cem

import scala.util.Properties._
import better.files._
import fr.janalyse.cem.externalities.publishadapter.AuthToken
import org.slf4j.LoggerFactory
import pureconfig.ConfigSource
import pureconfig.generic.auto._

case class ExamplesConfig(
  searchRootDirectories: String,
  searchGlob: Option[String],
) {
  val searchRoots =
    Option(searchRootDirectories)
      .map(_.split("""\s*,\s*""").toList)
      .getOrElse(Nil)
      .map(dir => dir.toFile)
      .filter(_.exists)
      .filter(_.isDirectory)
}

case class RenameRuleConfig(
  from:String,
  to:String,
) {
  def rename(input:String):String = {
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
) {
  val authToken = token.map(AuthToken)
}

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
  def name: String = projectName.getOrElse("project-name")
  def code: String = projectName.getOrElse("project-code")
  def version = buildVersion.getOrElse("x.y.z")
  def dateTime = buildDateTime.getOrElse("?")
  def uuid = buildUUID.getOrElse("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
  def projectURL = projectPage.getOrElse("https://github.com/dacr")
}

case class CodeExampleManagerConfig(
  examples: ExamplesConfig,
  publishAdapters: Map[String, PublishAdapterConfig],
  metaInfo: MetaConfig
)

case class Configuration(
  codeExamplesManagerConfig: CodeExampleManagerConfig
)

object Configuration {
  def apply(): CodeExampleManagerConfig = {
    val logger = LoggerFactory.getLogger("Configuration")
    val configSource = {
      val metaConfig = ConfigSource.resources("cem-meta.conf")
      ConfigSource.default.withFallback(metaConfig.optional)
    }
    configSource.load[Configuration] match {
      case Left(issues) =>
        issues.toList.foreach { issue => logger.error(issue.toString) }
        throw new RuntimeException("Invalid application configuration\n" + issues.toList.map(_.toString).mkString("\n"))
      case Right(config) =>
        config.codeExamplesManagerConfig
    }
  }
}