/*
 * Copyright 2023 David Crosson
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
import zio.config.*
import zio.config.magnolia.*
import zio.config.typesafe.*

import java.io.File
import scala.util.matching.Regex

case class ExamplesConfig(
  searchRootDirectories: String,
  searchOnlyPattern: Option[String],
  searchIgnoreMask: Option[String],
  charEncoding: String
) {
  def searchOnlyPatternRegex(): Option[Regex] = searchOnlyPattern.filterNot(_.trim.isEmpty).map(_.r)
  def searchIgnoreMaskRegex(): Option[Regex] = searchIgnoreMask.filterNot(_.trim.isEmpty).map(_.r)
}

case class RenameRuleConfig(
  from: String,
  to: String
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
  filenameRenameRules: Map[String, RenameRuleConfig]
) {
  def targetName = s"$kind/$activationKeyword"
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

case class SummaryConfig(
  title: String
)

case class CodeExampleManagerConfig(
  examples: ExamplesConfig,
  publishAdapters: Map[String, PublishAdapterConfig],
  metaInfo: MetaConfig,
  summary: SummaryConfig
)

case class ApplicationConfig(
  codeExamplesManagerConfig: CodeExampleManagerConfig
)

object ApplicationConfig {
  val config : Config[ApplicationConfig] = deriveConfig[ApplicationConfig].mapKey(toKebabCase)
}
