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
package fr.janalyse.cem.tools

import sttp.model.Uri
import zio.*

object HttpTools {
  def uriParse(link: String): Task[Uri] = {
    ZIO.from(Uri.parse(link)).mapError(msg => new Error(msg))
  }

  def webLinkingExtractNext(link: String): Option[String] = {
    // Using Web Linking to get large amount of results : https://tools.ietf.org/html/rfc5988
    val nextLinkRE = """.*<([^>]+)>; rel="next".*""".r
    nextLinkRE.findFirstMatchIn(link).map(_.group(1))
  }

}
