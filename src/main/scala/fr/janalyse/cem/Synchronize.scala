package fr.janalyse.cem

import zio.config.getConfig
import zio.{Has, IO, RIO, Runtime, Task, ZIO, ZLayer, clock}
import zio.logging._
import better.files._
import fr.janalyse.cem.model.{CodeExample, RemoteExampleState}
import sttp.client3.asynchttpclient.zio.{AsyncHttpClientZioBackend, SttpClient}
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

  def examplesFromGivenRoot(
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

  def examplesValidSearchRoots(searchRootDirectories: String): Task[List[SearchRoot]] = {
    val roots = searchRootDirectories.split("""\s*,\s*""").toList.map(_.trim)
    IO(roots.map(f => File(f)).tapEach(f => assert(f.isDirectory)).map(_.pathAsString))
  }


  def examplesCollectFor(searchRoots: List[SearchRoot], usingGlobPattern: SearchGlob): Task[Vector[CodeExample]] = {
    val examplesFromRoots = searchRoots.map(fromRoot => examplesFromGivenRoot(fromRoot, usingGlobPattern, findFiles, readFileContent))
    Task.mergeAll(examplesFromRoots)(Vector.empty[CodeExample])((accu, newRoot) => accu.appendedAll(newRoot))
  }

  def examplesCheckCoherency(examples: Iterable[CodeExample]): Task[Unit] = {
    val uuids = examples.flatMap(_.uuid)
    val duplicated = uuids.groupBy(u => u).filter { case (_, duplicated) => duplicated.size > 1 }.keys
    if (duplicated.nonEmpty)
      Task.fail(new Error("Found duplicated UUIDs : " + duplicated.mkString(",")))
    else Task.succeed(())
  }

  val examplesCollect: RIO[Has[ApplicationConfig], Vector[CodeExample]] = for {
    config <- getConfig[ApplicationConfig]
    examplesConfig = config.codeExamplesManagerConfig.examples
    searchRoots <- examplesValidSearchRoots(examplesConfig.searchRootDirectories)
    localExamples <- examplesCollectFor(searchRoots, examplesConfig.searchGlob)
    _ <- examplesCheckCoherency(localExamples)
  } yield localExamples


  def examplesPublishToGivenAdapter(
    examples: Iterable[CodeExample],
    adapterName:String,
    adapterConfig: PublishAdapterConfig,
    remoteExampleStatesFetcher: PublishAdapterConfig => RIO[Logging with SttpClient, Iterable[RemoteExampleState]]
  ): RIO[Logging with SttpClient, Unit] = {
    val examplesToSynchronize = examples.filter(_.publish.contains(adapterConfig.activationKeyword))
    if (!adapterConfig.enabled || examplesToSynchronize.isEmpty) RIO.succeed(Nil)
    else {
      for {
        remoteStates <- remoteExampleStatesFetcher(adapterConfig)
        _ <- log.info(s"Found ${remoteStates.size} examples already published examples on $adapterName (${adapterConfig.kind}/${adapterConfig.activationKeyword})")
      } yield ()
    }
  }


  def examplesPublish(examples: Vector[CodeExample], adaptersConfig: Map[String, PublishAdapterConfig]): RIO[Logging with SttpClient, Unit] = {
    val results = for {
      (adapterName, adapterConfig) <- adaptersConfig
    } yield {
      examplesPublishToGivenAdapter(examples, adapterName, adapterConfig, RemoteOperations.remoteExampleStatesFetch)
    }
    RIO.mergeAllPar(results)(())((accu, next) => next)
  }


  def synchronizeEffect: RIO[Logging with Clock with SttpClient with Has[ApplicationConfig], Unit] = for {
    startTime <- clock.nanoTime
    config <- getConfig[ApplicationConfig]
    adaptersConfig = config.codeExamplesManagerConfig.publishAdapters
    metaInfo = config.codeExamplesManagerConfig.metaInfo
    version = metaInfo.version
    appName = metaInfo.name
    appCode = metaInfo.code
    projectURL = metaInfo.projectURL
    _ <- log.info(s"$appName application is starting")
    _ <- log.info(s"$appCode version $version")
    _ <- log.info(s"$appCode project page $projectURL (with configuration documentation) ")
    examples <- examplesCollect
    _ <- log.info(s"Found ${examples.size} available locally for synchronization purpose")
    _ <- examplesPublish(examples, adaptersConfig)
    endTime <- clock.nanoTime
    _ <- log.info(s"Code examples manager publishing operations took ${(endTime - startTime) / 1000000}ms")
  } yield ()


  //@main
  def main(args: Array[String]): Unit = {
    val configLayer = ZLayer.fromEffect(ApplicationConfig())
    val httpClientLayer = AsyncHttpClientZioBackend.layer()
    val clockLayer = Clock.live

    val loggingLayer =
      Logging.console(
        logLevel = LogLevel.Info,
        format = LogFormat.ColoredLogFormat()
      ) >>> Logging.withRootLoggerName("Synchronize")

    val layers = configLayer ++ httpClientLayer ++ clockLayer ++ loggingLayer
    Runtime.default.unsafeRun(synchronizeEffect.provideLayer(layers))
  }
}


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