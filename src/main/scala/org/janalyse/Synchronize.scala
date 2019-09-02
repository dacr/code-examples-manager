package org.janalyse

import org.slf4j.{Logger, LoggerFactory}

object Synchronize {
  val logger: Logger = LoggerFactory.getLogger(getClass.getName)
  def now(): Long = System.currentTimeMillis()
  def howLong[T](proc: => T):(T, Long) = {
    val started = now()
    val result = proc
    result -> (now() - started)
  }

  def updateOverview(changes: List[Change])(implicit parameters:Parameters): Unit = {
    val exampleUUID = "cafacafe-cafecafe"
    val exampleSummary = "Example overview."
    val header =
      s"""# Example overview
         |- summary : $exampleSummary
         |- id : $exampleUUID
         |
         |## examples
         |""".stripMargin

    val exampleContent = for {
      change <- changes.sortBy(_.example.filename)
      filename = change.example.filename
      summary <- change.example.summary
      url <- change.publishedUrls.get("gist") // TODO - Hardcoded for gist
    } yield {
      s"- [$filename]($url) : $summary"
    }

    val example = new CodeExample {
      override val filename: String = "index.md"
      override val summary: Option[String] = Some(exampleSummary)
      override val keywords: List[String] = Nil
      override val publish: List[String] = Nil
      override val authors: List[String] = Nil
      override val uuid: Option[String] = Some(exampleUUID)

      override def content: String = header++exampleContent.mkString("\n")
      override def checksum: String = Hashes.sha1(content)
    }
    ExamplesManager.upsert(example)
  }

  def main(args: Array[String]): Unit = {
    logger.info("Started")
    val (_, duration) = howLong {
      implicit val parameters = Parameters()
      import ExamplesManager.{getExamples, synchronize, migrate}
      val examples = getExamples
      logger.info(s"Found ${examples.size} available locally for synchronization purpose")
      val uuids = examples.map(_.uuid).flatten
      val duplicated = uuids.groupBy(u => u).filter { case (_, duplicated) => duplicated.size > 1 }.keys
      assert(duplicated.size == 0, "Found duplicated UUIDs :" + duplicated.mkString(","))
      val changes = synchronize(examples)
      LogChanges(changes)
      updateOverview(changes)
    }
    logger.info(s"Finished in ${duration/1000}s")
  }

  private def LogChanges(changes: List[Change]) = {
    changes
      .filterNot(_.isInstanceOf[NoChange])
      .map(_.toString)
      .sorted
      .foreach(logger.info)
  }
}