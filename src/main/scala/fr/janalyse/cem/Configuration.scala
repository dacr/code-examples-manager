/*
 * Copyright 2022 David Crosson
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

import zio.*
import scala.util.Properties.*
import com.typesafe.config.{Config, ConfigFactory}
import zio.config.*
import zio.config.magnolia.*
import zio.config.typesafe.*
import zio.config.ConfigDescriptor.*

import java.io.File

final case class ExamplesConfig(
  searchRootDirectories: String,
  searchOnlyPattern: Option[String],
  searchIgnoreMask: Option[String],
  charEncoding: String
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
  def name: String       = projectName.getOrElse("code-examples-manager")
  def code: String       = projectName.getOrElse("cem")
  def version: String    = buildVersion.getOrElse("x.y.z")
  def dateTime: String   = buildDateTime.getOrElse("?")
  def uuid: String       = buildUUID.getOrElse("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
  def projectURL: String = projectPage.getOrElse("https://github.com/dacr")
  def contact: String    = contactEmail.getOrElse("crosson.david@gmail.com")
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
  def apply(): RIO[System & Console, ApplicationConfig] = {
    val metaConfigResourceName = "cem-meta.conf"

    for {
      configFileEnvOption  <- System.env("CEM_CONFIG_FILE")
      configFilePropOption <- System.property("CEM_CONFIG_FILE")
      configFileOption      = configFileEnvOption.orElse(configFilePropOption)
      typesafeConfig       = IO(loadTypesafeBasedConfigData(metaConfigResourceName, configFileOption))
      configSource         = TypesafeConfigSource.fromTypesafeConfig(typesafeConfig)
      config               <- zio.config.read(descriptor[ApplicationConfig].mapKey(toKebabCase) from configSource)
    } yield config
  }

  private def loadTypesafeBasedConfigData(metaConfigResourceName: String, configFileOption: Option[String]) = {
    val metaDataConfig    = ConfigFactory.load(metaConfigResourceName)
    val applicationConfig = configFileOption
      .map(f => ConfigFactory.parseFile(new File(f)))
      .getOrElse(ConfigFactory.load())
    ConfigFactory
      .empty()
      .withFallback(applicationConfig)
      .withFallback(metaDataConfig)
      .resolve()
  }
}
