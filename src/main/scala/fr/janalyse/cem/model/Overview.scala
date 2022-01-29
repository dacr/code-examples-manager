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
package fr.janalyse.cem.model

import fr.janalyse.cem.templates.txt.*
import fr.janalyse.cem.{CodeExampleManagerConfig, PublishAdapterConfig}
import zio.{RIO, Task}
import zio.logging.*

import java.time.Instant

case class OverviewContext(
  title: String,
  examplesCount: Int,
  examples: List[ExampleContext],
  examplesByCategory: List[ExamplesForCategoryContext],
  projectName: String,
  projectURL: String,
  version: String,
  lastUpdated: String
)
case class ExampleContext(category: String, filename: String, summary: String, url: String)
case class ExamplesForCategoryContext(category: String, categoryExamples: Seq[ExampleContext])

object Overview {

  def makeOverview(publishedExamples: Iterable[RemoteExample], adapter: PublishAdapterConfig, config: CodeExampleManagerConfig): RIO[Any, Option[CodeExample]] = {
    if (publishedExamples.isEmpty) RIO.none
    else {
      import fr.janalyse.tools.NaturalSort.ord
      val exampleContexts           = for {
        publishedExample <- publishedExamples.toSeq
        category          = publishedExample.example.category.getOrElse("Without category")
        filename          = publishedExample.example.filename
        summary           = publishedExample.example.summary.getOrElse("")
        url               = publishedExample.state.url
      } yield {
        ExampleContext(category = category, filename = filename, summary = summary, url = url)
      }
      val examplesContextByCategory =
        exampleContexts
          .groupBy(_.category)
          .toList
          .map { case (category, examplesByCategory) => ExamplesForCategoryContext(category, examplesByCategory.sortBy(_.filename)) }
          .sortBy(_.category)

      val overviewContext = OverviewContext(
        title = config.summary.title,
        examplesCount = exampleContexts.size,
        examples = exampleContexts.sortBy(_.summary).toList,
        examplesByCategory = examplesContextByCategory,
        projectName = config.metaInfo.name,
        projectURL = config.metaInfo.projectURL,
        version = config.metaInfo.version,
        lastUpdated = Instant.now().toString
      )
      val templateLogic   = for {
        //_ <- log.info(s"${adapter.targetName} : Generating overview")
        overviewContent <- Task.effect(ExamplesOverviewTemplate.render(overviewContext).body)
      } yield {
        CodeExample(
          filename = "index.md",
          category = None,
          summary = Some(config.summary.title),
          keywords = Nil,
          publish = List(adapter.activationKeyword),
          authors = Nil,
          uuid = Some(adapter.overviewUUID),
          content = overviewContent
        )
      }
      templateLogic.asSome
    }
  }

}
