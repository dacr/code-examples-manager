package fr.janalyse.cem

import zio.config.getConfig
import zio.{Has, IO, RIO, Runtime, Task, ZIO, ZLayer, clock}
import zio.logging._
import better.files._
import zio.clock.Clock

object Synchronize {

  type SearchRoot = String
  type SearchGlob = String
  type ExampleFilename = String
  type FileContent = String

  def readFileContent(fromFilename: ExampleFilename): Task[FileContent] = {
    Task(File(fromFilename).contentAsString)
  }

  def findFiles(fromRootFilename: SearchRoot, globPattern: SearchGlob): Task[Iterator[ExampleFilename]] = {
    Task(File(fromRootFilename).glob(globPattern, includePath = false).map(_.pathAsString))
  }

  def findCodeExamplesFromGivenRoot(
    searchRoot: SearchRoot,
    globPattern: SearchGlob,
    filesFetcher: (SearchRoot, SearchGlob) => Task[Iterator[ExampleFilename]],
    contentFetcher: ExampleFilename => Task[FileContent]
  ): Task[List[CodeExample]] = {
    for {
      foundFilenames <- filesFetcher(searchRoot, globPattern)
      examplesTasks = foundFilenames.map(file => CodeExample.makeExample(file, searchRoot, contentFetcher(file))).toList
      examples <- Task.mergeAll(examplesTasks)(List.empty[CodeExample])((accu, next) => next :: accu)
    } yield examples.filter(_.uuid.isDefined)

  }

  def validSearchRoots(searchRootDirectories: String): Task[List[SearchRoot]] = {
    val roots = searchRootDirectories.split("""\s*,\s*""").toList.map(_.trim)
    IO(roots.map(f => File(f)).tapEach(f => assert(f.isDirectory)).map(_.pathAsString))
  }


  def findCodeExamples(searchRoots: List[SearchRoot], usingGlobPattern: SearchGlob): Task[Vector[CodeExample]] = {
    val examplesFromRoots = searchRoots.map(fromRoot => findCodeExamplesFromGivenRoot(fromRoot, usingGlobPattern, findFiles, readFileContent))
    Task.mergeAll(examplesFromRoots)(Vector.empty[CodeExample])((accu, newRoot) => accu.appendedAll(newRoot))
  }

  def checkExamplesCoherency(examples: Iterable[CodeExample]): Task[Unit] = {
    val uuids = examples.flatMap(_.uuid)
    val duplicated = uuids.groupBy(u => u).filter { case (_, duplicated) => duplicated.size > 1 }.keys
    if (duplicated.nonEmpty)
      Task.fail(new Error("Found duplicated UUIDs : " + duplicated.mkString(",")))
    else Task.succeed(())
  }


  def publishExamplesToGitLab(examples: Iterable[CodeExample], adapterConfig: PublishAdapterConfig):RIO[Logging,Unit] = {
    RIO.succeed()
  }
  def publishExamplesToGitHub(examples: Iterable[CodeExample], adapterConfig: PublishAdapterConfig):RIO[Logging,Unit] = {
    RIO.succeed()
  }

  def publishExamples(examples: Vector[CodeExample], adaptersConfig: Map[String,PublishAdapterConfig]):RIO[Logging, Unit] = {
    val adaptersKeys = adaptersConfig.keySet
    val results = for {
      adapterKey <- adaptersKeys
      adapterConfig <- adaptersConfig.get(adapterKey)
      adapterExamplesToSynchronize = examples.filter(_.publish.contains(adapterKey))
    } yield {
      adapterConfig.kind match {
        case "gitlab" => publishExamplesToGitLab(adapterExamplesToSynchronize, adapterConfig)
        case "github" => publishExamplesToGitHub(adapterExamplesToSynchronize, adapterConfig)
      }
    }
    RIO.mergeAll(results)(())( (accu,next) => next)
  }

  def synchronize: RIO[Logging with Clock with Has[ApplicationConfig], Unit] = for {
    startTime <- clock.nanoTime
    config <- getConfig[ApplicationConfig]
    examplesConfig = config.codeExamplesManagerConfig.examples
    publishConfig = config.codeExamplesManagerConfig.publishAdapters
    metaInfo = config.codeExamplesManagerConfig.metaInfo
    version = metaInfo.version
    appName = metaInfo.name
    appCode = metaInfo.code
    projectURL = metaInfo.projectURL
    _ <- log.info(s"$appName application is starting")
    _ <- log.info(s"$appCode version $version")
    _ <- log.info(s"$appCode project page $projectURL (with configuration documentation) ")
    searchRoots <- validSearchRoots(examplesConfig.searchRootDirectories)
    localExamples <- findCodeExamples(searchRoots, examplesConfig.searchGlob)
    _ <- checkExamplesCoherency(localExamples)
    _ <- log.info(s"Found ${localExamples.size} available locally for synchronization purpose")
    _ <- publishExamples(localExamples, publishConfig)
    endTime <- clock.nanoTime
    _ <- log.info(s"Code examples manager publishing operations took ${(endTime - startTime) / 1000000}ms")
  } yield ()


  //@main
  def main(args: Array[String]): Unit = {
    val configLayer = ZLayer.fromEffect(ApplicationConfig())

    val loggingLayer =
      Logging.console(
        logLevel = LogLevel.Info,
        format = LogFormat.ColoredLogFormat()
      ) >>> Logging.withRootLoggerName("Synchronize")

    Runtime.default.unsafeRun(synchronize.provideLayer(configLayer ++ loggingLayer ++ Clock.live))
  }
}

//
//import fr.janalyse.cem.externalities.publishadapter.PublishAdapter
//import fr.janalyse.cem.externalities.publishadapter.github.GithubPublishAdapter
//import fr.janalyse.cem.externalities.publishadapter.gitlab.GitlabPublishAdapter
//import org.slf4j.{Logger, LoggerFactory}
//
//object Synchronize {
//

//
//  def main(args: Array[String]): Unit = {
//    val version = config.metaInfo.version
//    val appName = config.metaInfo.name
//    val appCode = config.metaInfo.code
//    val projectURL = config.metaInfo.projectURL
//    logger.info(s"$appName application is starting")
//    logger.info(s"$appCode version $version")
//    logger.info(s"$appCode project page $projectURL (with configuration documentation) ")
//
//    val (_, duration) = howLong {
//      val availableLocalExamples = ExamplesManager.getExamples(config)
//      logger.info(s"Found ${availableLocalExamples.size} available locally for synchronization purpose")
//      localExamplesCoherency(availableLocalExamples)
//
//      for {
//        (adapterConfigName, adapterConfig) <- config.publishAdapters
//        if adapterConfig.enabled
//        adapter <- searchForAdapter(adapterConfig)
//        examplesForCurrentAdapter = availableLocalExamples.filter(_.publish.contains(adapterConfig.activationKeyword))
//      } {
//        try {
//          publish(adapterConfigName, examplesForCurrentAdapter, adapter)
//        } catch {
//          case ex:Exception =>
//            logger.error(s"Can't publish with $adapterConfigName", ex)
//        }
//      }
//    }
//    logger.info(s"Code examples manager publishing operations took ${duration / 1000}s")
//  }
//
//  private def searchForAdapter(adapterConfig: PublishAdapterConfig) = {
//    adapterConfig.kind match {
//      case "gitlab" =>
//        GitlabPublishAdapter.lookup(adapterConfig)
//      case "github" =>
//        GithubPublishAdapter.lookup(adapterConfig)
//      case unrecognized =>
//        logger.warn(s"Unrecognized adapter kind $unrecognized, only [gitlab|github] are supported")
//        None
//    }
//  }
//
//  private def publish(adapterConfigName: String, examplesForCurrentAdapter: List[CodeExample], adapter: PublishAdapter): Unit = {
//    logger.info(s"$adapterConfigName : Synchronizing ${examplesForCurrentAdapter.size} examples using ${adapter.getClass.getName}")
//    val changes = ExamplesManager.synchronize(examplesForCurrentAdapter, adapter)
//    LogChanges(changes)
//    val overviewChange = Overview.updateOverview(changes, adapter, config)
//    val overviewMessage = s"$adapterConfigName : Examples overview is available at ${overviewChange.publishedUrl.getOrElse("")}"
//    logger.info(overviewMessage)
//    println(overviewMessage)
//  }
//
//  private def LogChanges(changes: Seq[Change]): Unit = {
//    changes
//      .filterNot(_.isInstanceOf[NoChange])
//      .map(_.toString)
//      .sorted
//      .foreach(logger.info)
//  }
//}