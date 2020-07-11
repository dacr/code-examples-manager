package fr.janalyse.cem

import fr.janalyse.cem.externalities.publishadapter.PublishAdapter


case class OverviewContext(
  examplesCount:Int,
  examples: List[ExampleContext],
  examplesByCategory: List[ExamplesForCategoryContext]
)
case class ExampleContext(category:String, filename:String, summary:String, url:String)
case class ExamplesForCategoryContext(category: String, categoryExamples: Seq[ExampleContext])


object Overview {

  def updateOverview(changes: Seq[Change], adapter: PublishAdapter, config: CodeExampleManagerConfig): Change = {
    val exampleContexts = for {
      change <- changes
      category = change.example.category.getOrElse("Without category")
      filename = change.example.filename
      summary = change.example.summary.getOrElse("")
      url <- change.publishedUrl
    } yield {
      ExampleContext(category = category, filename = filename, summary = summary,url = url)
    }
    val examplesContextByCategory =
      exampleContexts
        .groupBy(_.category)
        .toList
        .map{case (category, examplesByCategory) => ExamplesForCategoryContext(category, examplesByCategory.sortBy(_.filename))}
        .sortBy(_.category)

    val overviewContext = OverviewContext(
      examplesCount = exampleContexts.size,
      examples = exampleContexts.sortBy(_.summary).toList,
      examplesByCategory = examplesContextByCategory
    )
    val overviewContent = YamuscaTemplating(config).layout("templates/examples-overview.mustache", overviewContext)

    val overview = new CodeExample {
      override val filename: String = "index.md"
      override val category: Option[String] = None
      override val summary: Option[String] = Some("Examples overview.")
      override val keywords: List[String] = Nil
      override val publish: List[String] = Nil
      override val authors: List[String] = Nil
      override val uuid: Option[String] = Some(adapter.config.overviewUUID)

      override def content: String = overviewContent

      override def checksum: String = Hashes.sha1(overviewContent)
    }
    ExamplesManager.upsert(overview, adapter)
  }


}
