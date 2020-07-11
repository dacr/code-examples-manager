/*
 * Copyright 2020 David Crosson
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

import yamusca.imports._
import yamusca.implicits._


// TODO - first implementation waiting for refactoring
case class YamuscaTemplating(config: CodeExampleManagerConfig) {
  def layout(templateName: String, context: OverviewContext): String = {
    import yamusca.imports._
    import yamusca.implicits._
    implicit val exampleConverter = ValueConverter.deriveConverter[ExampleContext]
    implicit val examplesForCategoryConverter = ValueConverter.deriveConverter[ExamplesForCategoryContext]
    implicit val overviewConverter = ValueConverter.deriveConverter[OverviewContext]
    val templateInput = YamuscaTemplating.getClass().getClassLoader().getResourceAsStream(templateName)
    val templateString = scala.io.Source.fromInputStream(templateInput).iterator.mkString
    context.unsafeRender(templateString)
  }
}
