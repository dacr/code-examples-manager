package fr.janalyse.cem.model

import fr.janalyse.cem.tools.TemplateEngine
import fr.janalyse.cem.{CodeExampleManagerConfig, PublishAdapterConfig}
import zio.{RIO, Task}
import zio.logging._

import java.time.Instant

case class OverviewContext(
  examplesCount:Int,
  examples: List[ExampleContext],
  examplesByCategory: List[ExamplesForCategoryContext],
  projectName: String,
  projectURL: String,
  version: String,
  lastUpdated: String
)
case class ExampleContext(category:String, filename:String, summary:String, url:String)
case class ExamplesForCategoryContext(category: String, categoryExamples: Seq[ExampleContext])



object Overview {

  def makeOverview(publishedExamples: Iterable[RemoteExample], adapter: PublishAdapterConfig, config: CodeExampleManagerConfig): RIO[Logging, Option[CodeExample]] = {
    if (publishedExamples.isEmpty) RIO.none else {
      import fr.janalyse.tools.NaturalSort.ord
      val exampleContexts = for {
        publishedExample <- publishedExamples.toSeq
        category = publishedExample.example.category.getOrElse("Without category")
        filename = publishedExample.example.filename
        summary = publishedExample.example.summary.getOrElse("")
        url = publishedExample.state.url
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
        examplesCount = exampleContexts.size,
        examples = exampleContexts.sortBy(_.summary).toList,
        examplesByCategory = examplesContextByCategory,
        projectName = config.metaInfo.name,
        projectURL = config.metaInfo.projectURL,
        version = config.metaInfo.version,
        lastUpdated = Instant.now().toString
      )
      val templateName = "templates/examples-overview.mustache"
      val templateLogic = for {
        //_ <- log.info(s"${adapter.targetName} : Generating overview")
        overviewContent <- Task.effect(TemplateEngine.layout(config, templateName, overviewContext))
      } yield {
        CodeExample(
          filename = "index.md",
          category = None,
          summary = Some("Programming knowledge base examples overview"),
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
