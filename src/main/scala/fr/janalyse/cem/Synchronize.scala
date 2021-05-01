package fr.janalyse.cem

import zio.config.getConfig
import zio.{Has, IO, RIO, Runtime, Task, ZIO, ZLayer, clock}
import zio.logging._
import better.files._
import fr.janalyse.cem.model._
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


  def computeWorkToDo(examples: Iterable[CodeExample], states: Iterable[RemoteExampleState]): List[WhatToDo] = {
    val statesByUUID = states.map(state => state.uuid -> state).toMap
    val examplesByUUID = examples.flatMap(example => example.uuid.map(_ -> example)).toMap
    val examplesUUIDs = examplesByUUID.keys.toSet
    val examplesTriple: Iterable[(Option[String], Option[CodeExample], Option[RemoteExampleState])] =
      examples
        .map(example => (example.uuid, Some(example), example.uuid.flatMap(statesByUUID.get))) ++
        states
          .filterNot(state => examplesUUIDs.contains(state.uuid))
          .map(state => (Some(state.uuid), examplesByUUID.get(state.uuid), Some(state)))
    examplesTriple.toSet.toList.map { triple: (Option[String], Option[CodeExample], Option[RemoteExampleState]) =>
      triple match {
        case (None, Some(example), None) => IgnoreExample(example)
        case (Some(uuid), None, Some(state)) => OrphanRemoteExample(uuid, state)
        case (Some(uuid), Some(example), None) => AddExample(uuid, example)
        case (Some(uuid), Some(example), Some(state)) if example.checksum == state.checksum => KeepRemoteExample(uuid, example, state)
        case (Some(uuid), Some(example), Some(state)) if example.checksum != state.checksum => UpdateRemoteExample(uuid, example, state)
        case (x, y, z) => UnsupportedOperation(x, y, z)
      }
    }
  }

  def checkRemote(adapterConfig: PublishAdapterConfig)(todo: WhatToDo): RIO[Logging, Unit] = {
    todo match {
      case UnsupportedOperation(uuidOption, exampleOption, stateOption) =>
        log.info(s"${adapterConfig.targetName} : Invalid input $uuidOption - $exampleOption - $stateOption")
      case OrphanRemoteExample(uuid, state) if uuid != adapterConfig.overviewUUID =>
        log.info(s"${adapterConfig.targetName} : Found orphan example $uuid - ${state.description} - ${state.url}")
      case _: OrphanRemoteExample => RIO.unit
      case _: IgnoreExample => RIO.unit
      case _: KeepRemoteExample => RIO.unit
      case _: UpdateRemoteExample => RIO.unit
      case _: AddExample => RIO.unit
    }
  }

  def checkCoherency(adapterConfig: PublishAdapterConfig, todos: Iterable[WhatToDo]): RIO[Logging, Unit] = {
    for {
      _ <- RIO.foreach(todos)(checkRemote(adapterConfig))
    } yield ()
  }

  def examplesPublishToGivenAdapter(
    examples: Iterable[CodeExample],
    adapterConfig: PublishAdapterConfig,
    config: CodeExampleManagerConfig,
    remoteExampleStatesFetcher: PublishAdapterConfig => RIO[Logging with SttpClient, Iterable[RemoteExampleState]],
    remoteExamplesChangesApplier: (PublishAdapterConfig, Iterable[WhatToDo]) => RIO[Logging with SttpClient, Iterable[RemoteExample]]
  ): RIO[Logging with SttpClient, Unit] = {
    val examplesToSynchronize = examples.filter(_.publish.contains(adapterConfig.activationKeyword))
    if (!adapterConfig.enabled || examplesToSynchronize.isEmpty) RIO.succeed(Nil)
    else {
      for {
        remoteStates <- remoteExampleStatesFetcher(adapterConfig)
        _ <- log.info(s"${adapterConfig.targetName} : Found ${remoteStates.size}  already published artifacts")
        _ <- log.info(s"${adapterConfig.targetName} : Found ${examplesToSynchronize.size} synchronisable examples")
        todos = computeWorkToDo(examplesToSynchronize, remoteStates)
        _ <- checkCoherency(adapterConfig, todos)
        remoteExamples <- remoteExamplesChangesApplier(adapterConfig, todos)
        //_ <- log.info(s"${adapterConfig.targetName} : Build examples summary")
        overviewOption <- Overview.makeOverview(remoteExamples, adapterConfig, config)
        //_ <- log.info(s"${adapterConfig.targetName} : Publish examples summary")
        overviewTodo = computeWorkToDo(overviewOption, remoteStates)
        remoteOverview <- remoteExamplesChangesApplier(adapterConfig, overviewTodo)
        _ <- RIO.foreach(remoteOverview.headOption)(publishedOverview =>
          log.info(s"${adapterConfig.targetName} : Summary available at ${publishedOverview.state.url}"))
      } yield ()
    }
  }


  def examplesPublish(examples: Vector[CodeExample], config: CodeExampleManagerConfig): RIO[Logging with SttpClient, Unit] = {
    val results = for {
      (adapterName, adapterConfig) <- config.publishAdapters
    } yield {
      examplesPublishToGivenAdapter(
        examples,
        adapterConfig,
        config,
        RemoteOperations.remoteExampleStatesFetch,
        RemoteOperations.remoteExamplesChangesApply
      )
    }
    RIO.mergeAllPar(results)(())((accu, next) => next)
  }


  def synchronizeEffect: RIO[Logging with Clock with SttpClient with Has[ApplicationConfig], Unit] = for {
    startTime <- clock.nanoTime
    config <- getConfig[ApplicationConfig].map(_.codeExamplesManagerConfig)
    metaInfo = config.metaInfo
    version = metaInfo.version
    appName = metaInfo.name
    appCode = metaInfo.code
    projectURL = metaInfo.projectURL
    _ <- log.info(s"$appName application is starting")
    _ <- log.info(s"$appCode version $version")
    _ <- log.info(s"$appCode project page $projectURL (with configuration documentation) ")
    examples <- examplesCollect //.map(_.filter(_.category==Some("cem/tests")))
    _ <- log.info(s"Found ${examples.size} available locally for synchronization purpose")
    _ <- examplesPublish(examples, config)
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
