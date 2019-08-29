package org.janalyse

import org.slf4j.{Logger, LoggerFactory}

object Synchronize {
  val logger: Logger = LoggerFactory.getLogger(getClass.getName)

  def main(args: Array[String]): Unit = {
    logger.info("Started")
    implicit val parameters = Parameters()
    import ExamplesManager.{getExamples,synchronize, migrate}
    val examples = getExamples
    logger.info(s"Found ${examples.size} available locally for synchronization purpose")
    val uuids = examples.map(_.uuid).flatten
    val duplicated = uuids.groupBy(u => u).filter{case (_, duplicated) => duplicated.size > 1}.keys
    assert(duplicated.size==0, "Found duplicated UUIDs :"+duplicated.mkString(","))
    val changes = synchronize(examples)
    changes
      .map(_.toString)
      .sorted
      .foreach(logger.info)
    logger.info("Finished")
  }
}