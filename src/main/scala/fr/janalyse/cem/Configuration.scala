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

case class CodeExampleManagerConfig(
  examples: ExamplesConfig,
  publishAdapters: Map[String, PublishAdapterConfig],
)

case class Configuration(
  codeExamplesManagerConfig: CodeExampleManagerConfig
)

object Configuration {
  def apply(): CodeExampleManagerConfig = {
    val logger = LoggerFactory.getLogger("Configuration")
    ConfigSource.default.load[Configuration] match {
      case Left(issues) =>
        issues.toList.foreach { issue => logger.error(issue.toString) }
        throw new RuntimeException("Invalid application configuration\n" + issues.toList.map(_.toString).mkString("\n"))
      case Right(config) =>
        config.codeExamplesManagerConfig
    }
  }
}