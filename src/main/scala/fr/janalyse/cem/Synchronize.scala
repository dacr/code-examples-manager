package fr.janalyse.cem

import fr.janalyse.cem.externalities.publishadapter.PublishAdapter
import fr.janalyse.cem.externalities.publishadapter.github.GithubPublishAdapter
import fr.janalyse.cem.externalities.publishadapter.gitlab.GitlabPublishAdapter
import org.slf4j.{Logger, LoggerFactory}

object Synchronize {
  val logger: Logger = LoggerFactory.getLogger(getClass.getName)
  val config: CodeExampleManagerConfig = Configuration()

  def now(): Long = System.currentTimeMillis()

  def howLong[T](proc: => T): (T, Long) = {
    val started = now()
    val result = proc
    result -> (now() - started)
  }

  def localExamplesCoherency(examples: List[CodeExample]): Unit = {
    val uuids = examples.flatMap(_.uuid)
    val duplicated = uuids.groupBy(u => u).filter { case (_, duplicated) => duplicated.size > 1 }.keys
    assert(duplicated.isEmpty, "Found duplicated UUIDs : " + duplicated.mkString(","))
  }


  def main(args: Array[String]): Unit = {
    logger.info("Code examples manager started")
    val (_, duration) = howLong {
      val availableLocalExamples = ExamplesManager.getExamples(config)
      logger.info(s"Found ${availableLocalExamples.size} available locally for synchronization purpose")
      localExamplesCoherency(availableLocalExamples)

      for {
        (adapterConfigName, adapterConfig) <- config.publishAdapters
        if adapterConfig.enabled
        adapter <- searchForAdapter(adapterConfig)
        examplesForCurrentAdapter = availableLocalExamples.filter(_.publish.contains(adapterConfig.activationKeyword))
      } {
        try {
          publish(adapterConfigName, examplesForCurrentAdapter, adapter)
        } catch {
          case ex:Exception =>
            logger.error(s"Can't publish with $adapterConfigName", ex)
        }
      }
    }
    logger.info(s"Code examples manager publishing operations took ${duration / 1000}s")
  }

  private def searchForAdapter(adapterConfig: PublishAdapterConfig) = {
    adapterConfig.kind match {
      case "gitlab" =>
        GitlabPublishAdapter.lookup(adapterConfig)
      case "github" =>
        GithubPublishAdapter.lookup(adapterConfig)
      case unrecognized =>
        logger.warn(s"Unrecognized adapter kind $unrecognized, only [gitlab|github] are supported")
        None
    }
  }

  private def publish(adapterConfigName: String, examplesForCurrentAdapter: List[CodeExample], adapter: PublishAdapter) = {
    logger.info(s"$adapterConfigName : Synchronizing ${examplesForCurrentAdapter.size} examples using ${adapter.getClass.getName}")
    val changes = ExamplesManager.synchronize(examplesForCurrentAdapter, adapter)
    LogChanges(changes)
    val overviewChange = Overview.updateOverview(changes, adapter, config)
    val overviewMessage = s"$adapterConfigName : Examples overview is available at ${overviewChange.publishedUrl.getOrElse("")}"
    logger.info(overviewMessage)
    println(overviewMessage)
  }

  private def LogChanges(changes: Seq[Change]): Unit = {
    changes
      .filterNot(_.isInstanceOf[NoChange])
      .map(_.toString)
      .sorted
      .foreach(logger.info)
  }
}