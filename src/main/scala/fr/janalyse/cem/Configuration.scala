/*
 * Copyright 2021 David Crosson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.janalyse.cem

import scala.util.Properties.*
import com.typesafe.config.{Config, ConfigFactory}
import zio.{IO, RIO, ZIO, system}
import zio.config.*
import zio.config.typesafe.*
import zio.config.ConfigDescriptor.*
import zio.config.ConfigSource.*

final case class ExamplesConfig(
  searchRootDirectories: String,
  searchGlob: String,
  searchIgnoreMask: Option[String]
)

final case class RenameRuleConfig(
  from: String,
  to: String
) {
  def rename(input: String): String = {
    if (input.matches(from)) {
      input.replaceAll(from, to)
    } else input
  }
}

final case class PublishAdapterConfig(
  enabled: Boolean,
  kind: String,
  activationKeyword: String,
  apiEndPoint: String,
  overviewUUID: String,
  token: Option[String],
  defaultVisibility: Option[String],
  filenameRenameRules: Map[String, RenameRuleConfig]
) {
  def targetName = s"$kind/$activationKeyword"
}

// Automatically populated by the build process from a generated config file
final case class MetaConfig(
  projectName: Option[String],
  projectGroup: Option[String],
  projectPage: Option[String],
  projectCode: Option[String],
  buildVersion: Option[String],
  buildDateTime: Option[String],
  buildUUID: Option[String],
  contactEmail: Option[String]
) {
  def name: String = projectName.getOrElse("code-examples-manager")

  def code: String = projectName.getOrElse("cem")

  def version: String = buildVersion.getOrElse("x.y.z")

  def dateTime: String = buildDateTime.getOrElse("?")

  def uuid: String = buildUUID.getOrElse("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

  def projectURL: String = projectPage.getOrElse("https://github.com/dacr")

  def contact: String = contactEmail.getOrElse("crosson.david@gmail.com")
}

final case class SummaryConfig(
  title: String
)

final case class CodeExampleManagerConfig(
  examples: ExamplesConfig,
  publishAdapters: Map[String, PublishAdapterConfig],
  metaInfo: MetaConfig,
  summary: SummaryConfig
)

final case class ApplicationConfig(
  codeExamplesManagerConfig: CodeExampleManagerConfig
)

object Configuration {

  val examplesConfig = (
    string("search-root-directories") |@|
      string("search-glob") |@|
      string("search-ignore-mask").optional
  ).to[ExamplesConfig]

  val renameRuleConfig = (
    string("from") |@|
      string("to")
  ).to[RenameRuleConfig]

  val publishAdapterConfig = (
    boolean("enabled") |@|
      string("kind") |@|
      string("activation-keyword") |@|
      string("api-end-point") |@|
      string("overview-uuid") |@|
      string("token").optional |@|
      string("default-visibility").optional |@|
      map("filename-rename-rules")(renameRuleConfig)
  ).to[PublishAdapterConfig]

  val metaConfig = (
    string("project-name").optional |@|
      string("project-group").optional |@|
      string("project-page").optional |@|
      string("project-code").optional |@|
      string("build-version").optional |@|
      string("build-date-time").optional |@|
      string("build-uuid").optional |@|
      string("contact-email").optional
  ).to[MetaConfig]

  val summaryConfig = (
    string("title")
  ).to[SummaryConfig]

  val codeExampleManagerConfig: ConfigDescriptor[CodeExampleManagerConfig] = (
    nested("examples")(examplesConfig) |@|
      map("publish-adapters")(publishAdapterConfig) |@|
      nested("meta-info")(metaConfig) |@|
      nested("summary")(summaryConfig)
  ).to[CodeExampleManagerConfig]

  val applicationConfig: ConfigDescriptor[ApplicationConfig] = (
    nested("code-examples-manager-config")(codeExampleManagerConfig)
  ).to[ApplicationConfig]

  def apply(): RIO[system.System, ApplicationConfig] = {
    val metaConfigResourceName = "cem-meta.conf"

    for {
      configFileEnvOption  <- zio.system.env("CEM_CONFIG_FILE")
      configFilePropOption <- zio.system.property("CEM_CONFIG_FILE")
      configFileOption      = configFileEnvOption.orElse(configFilePropOption)
      typesafeConfig       <- IO(
                                ConfigFactory
                                  .empty()
                                  .withFallback(
                                    configFileOption
                                      .map(f => ConfigFactory.parseFile(new java.io.File(f)))
                                      .getOrElse(ConfigFactory.load())
                                  )
                                  .withFallback(ConfigFactory.load(metaConfigResourceName))
                                  .resolve()
                              )
      configSource         <- IO.fromEither(TypesafeConfigSource.fromTypesafeConfig(typesafeConfig))
      config               <- IO.fromEither(zio.config.read(applicationConfig from configSource))
    } yield config
  }

}
