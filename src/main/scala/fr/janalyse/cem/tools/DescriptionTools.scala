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
package fr.janalyse.cem.tools

import fr.janalyse.cem.PublishAdapterConfig
import fr.janalyse.cem.model.CodeExample

object DescriptionTools {
  private val metaDataRE = """#\s*([-0-9a-f]+)\s*/\s*([0-9a-f]+)\s*$""".r.unanchored

  def extractMetaDataFromDescription(description: String): Option[(String, String)] = {
    metaDataRE
      .findFirstMatchIn(description)
      .filter(_.groupCount == 2)
      .map(m => (m.group(1), m.group(2)))
  }

  def makeDescription(example: CodeExample): Option[String] = {
    for {
      summary <- example.summary.orElse(Some(""))
      uuid    <- example.uuid
      chksum   = example.checksum
      cemURL   = "https://github.com/dacr/code-examples-manager"
    } yield s"$summary / published by $cemURL #$uuid/$chksum"
  }

  /** rename file only on the remote publish site in order to take benefit of colorization feature
    * @param filename
    *   @param config
    * @return
    */
  def remoteExampleFileRename(filename: String, config: PublishAdapterConfig): String = {
    config.filenameRenameRules.values.foldLeft(filename) { (current, rule) => rule.rename(current) }
  }
}
