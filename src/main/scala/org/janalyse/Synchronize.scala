package org.janalyse

import org.slf4j.{Logger, LoggerFactory}

object Synchronize {
  val logger: Logger = LoggerFactory.getLogger(getClass.getName)

  def main(args: Array[String]): Unit = {
    logger.info("Started")
    logger.info("Finished")
  }
}