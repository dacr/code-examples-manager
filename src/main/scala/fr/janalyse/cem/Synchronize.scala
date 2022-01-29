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
package fr.janalyse.cem

import zio.*
import zio.config.getConfig
import zio.stream.*
import zio.nio.file.*
import zio.nio.charset.Charset
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitOption, OpenOption, StandardOpenOption}
import zio.nio.file.Files

import fr.janalyse.cem.model.*
import fr.janalyse.cem.model.WhatToDo.*
import sttp.client3.asynchttpclient.zio.{AsyncHttpClientZioBackend, SttpClient}

object Synchronize {
  type SearchRoot      = String
  type SearchGlob      = String
  type ExampleFilename = String
  type FileContent     = String
  type IgnoreMask      = String

  val charset = Charset.Standard.utf8

  def readFileContent(fromFilename: ExampleFilename): RIO[Any, FileContent] = {
    for {
      bytesRead <- Files.readAllBytes(Path(fromFilename))
      content   <- charset.decodeString(bytesRead)
    } yield content
  }

  def findFiles(fromRootFilename: SearchRoot, globPattern: SearchGlob, ignoreMask: Option[IgnoreMask] = None): RIO[Any, List[ExampleFilename]] = {
    val pathMatcher                                                     = FileSystem.default.getPathMatcher(s"glob:$globPattern")
    val ignoreMaskRegex                                                 = ignoreMask.map(_.r)
    def pathFilter(path: Path, fileAttrs: BasicFileAttributes): Boolean = {
      pathMatcher.matches(path.toFile.toPath) &&
      !fileAttrs.isDirectory &&
      (ignoreMask.isEmpty || ignoreMaskRegex.get.findFirstIn(path.toString).isEmpty)
    }
    val from                                                            = Path(fromRootFilename)
    val foundFilesStream                                                = Files.find(from)(pathFilter)
    val foundFiles                                                      = for {
      foundFiles <- foundFilesStream.run(ZSink.collectAll)
    } yield foundFiles.to(List).map(_.toFile.getPath) // TODO
    foundFiles
  }

  def examplesFromGivenRoot(
    searchRoot: SearchRoot,
    globPattern: SearchGlob,
    ignoreMask: Option[IgnoreMask],
    filesFetcher: (SearchRoot, SearchGlob, Option[IgnoreMask]) => RIO[Any, List[ExampleFilename]],
    contentFetcher: ExampleFilename => RIO[Any, FileContent]
  ): RIO[Any, List[CodeExample]] = {
    for {
      foundFilenames <- filesFetcher(searchRoot, globPattern, ignoreMask)
      examplesTasks   = foundFilenames.map(file => CodeExample.makeExample(file, searchRoot, contentFetcher(file))).toList
      examples       <- RIO.mergeAll(examplesTasks)(List.empty[CodeExample])((accu, next) => next :: accu)
    } yield examples.filter(_.uuid.isDefined)

  }

  def examplesValidSearchRoots(searchRootDirectories: String): RIO[Any, List[SearchRoot]] = {
    for {
      roots         <- Task(searchRootDirectories.split("""\s*,\s*""").toList.map(_.trim).map(r => Path(r)))
      validRoots    <- ZIO.filter(roots)(root => Files.isDirectory(root))
      pathsAsStrings = validRoots.map(_.toFile.getPath)
    } yield pathsAsStrings
  }

  def examplesCollectFor(searchRoots: List[SearchRoot], usingGlobPattern: SearchGlob, usingIgnoreMask: Option[IgnoreMask]): RIO[Any, Vector[CodeExample]] = {
    val examplesFromRoots = searchRoots.map(fromRoot => examplesFromGivenRoot(fromRoot, usingGlobPattern, usingIgnoreMask, findFiles, readFileContent))
    RIO.mergeAll(examplesFromRoots)(Vector.empty[CodeExample])((accu, newRoot) => accu.appendedAll(newRoot))
  }

  def examplesCheckCoherency(examples: Iterable[CodeExample]): Task[Unit] = {
    val uuids      = examples.flatMap(_.uuid)
    val duplicated = uuids.groupBy(u => u).filter { case (_, duplicated) => duplicated.size > 1 }.keys
    if (duplicated.nonEmpty)
      Task.fail(new Error("Found duplicated UUIDs : " + duplicated.mkString(",")))
    else Task.succeed(())
  }

  val examplesCollect = for {
    config        <- getConfig[ApplicationConfig]
    examplesConfig = config.codeExamplesManagerConfig.examples
    searchRoots   <- examplesValidSearchRoots(examplesConfig.searchRootDirectories)
    _             <- ZIO.logInfo(s"Searching examples in ${searchRoots.mkString(",")}")
    localExamples <- examplesCollectFor(searchRoots, examplesConfig.searchGlob, examplesConfig.searchIgnoreMask)
    _             <- examplesCheckCoherency(localExamples)
  } yield localExamples

  def computeWorkToDo(examples: Iterable[CodeExample], states: Iterable[RemoteExampleState]): List[WhatToDo] = {
    val statesByUUID   = states.map(state => state.uuid -> state).toMap
    val examplesByUUID = examples.flatMap(example => example.uuid.map(_ -> example)).toMap
    val examplesUUIDs  = examplesByUUID.keys.toSet
    val examplesTriple = { // (exampleUUID, foundLocalExample, foundRemoteExampleState)
      examples
        .map(example => (example.uuid, Some(example), example.uuid.flatMap(statesByUUID.get))) ++
        states
          .filterNot(state => examplesUUIDs.contains(state.uuid))
          .map(state => (Some(state.uuid), examplesByUUID.get(state.uuid), Some(state)))
    }
    examplesTriple.toSet.toList.collect {
      case (None, Some(example), None)                                                    => IgnoreExample(example)
      case (Some(uuid), None, Some(state))                                                => OrphanRemoteExample(uuid, state)
      case (Some(uuid), Some(example), None)                                              => AddExample(uuid, example)
      case (Some(uuid), Some(example), Some(state)) if example.checksum == state.checksum => KeepRemoteExample(uuid, example, state)
      case (Some(uuid), Some(example), Some(state)) if example.checksum != state.checksum => UpdateRemoteExample(uuid, example, state)
      case (x, y, z)                                                                      => UnsupportedOperation(x, y, z)
    }
  }

  def checkRemote(adapterConfig: PublishAdapterConfig)(todo: WhatToDo): RIO[Any, Unit] = {
    todo match {
      case UnsupportedOperation(uuidOption, exampleOption, stateOption)           => ZIO.logInfo(s"${adapterConfig.targetName} : Invalid input $uuidOption - $exampleOption - $stateOption")
      case OrphanRemoteExample(uuid, state) if uuid != adapterConfig.overviewUUID => ZIO.logInfo(s"${adapterConfig.targetName} : Found orphan example $uuid - ${state.description} - ${state.url}")
      case _: OrphanRemoteExample                                                 => ZIO.unit
      case _: IgnoreExample                                                       => ZIO.unit
      case _: KeepRemoteExample                                                   => ZIO.unit
      case _: UpdateRemoteExample                                                 => ZIO.unit
      case _: AddExample                                                          => ZIO.unit
      case _: DeleteRemoteExample                                                 => ZIO.unit
    }
  }

  def checkCoherency(adapterConfig: PublishAdapterConfig, todos: Iterable[WhatToDo]): RIO[Any, Unit] = {
    for {
      _ <- RIO.foreach(todos)(checkRemote(adapterConfig))
    } yield ()
  }

  def examplesPublishToGivenAdapter(
    examples: Iterable[CodeExample],
    adapterConfig: PublishAdapterConfig,
    config: CodeExampleManagerConfig,
    remoteExampleStatesFetcher: PublishAdapterConfig => RIO[SttpClient, Iterable[RemoteExampleState]],
    remoteExamplesChangesApplier: (PublishAdapterConfig, Iterable[WhatToDo]) => RIO[SttpClient, Iterable[RemoteExample]]
  ): RIO[SttpClient, Unit] = {
    val examplesToSynchronize = examples.filter(_.publish.contains(adapterConfig.activationKeyword))
    if (!adapterConfig.enabled || examplesToSynchronize.isEmpty || adapterConfig.token.isEmpty) RIO.unit
    else {
      for {
        remoteStates   <- remoteExampleStatesFetcher(adapterConfig)
        _              <- ZIO.logInfo(s"${adapterConfig.targetName} : Found ${remoteStates.size}  already published artifacts")
        _              <- ZIO.logInfo(s"${adapterConfig.targetName} : Found ${examplesToSynchronize.size} synchronisable examples")
        todos           = computeWorkToDo(examplesToSynchronize, remoteStates)
        _              <- checkCoherency(adapterConfig, todos)
        remoteExamples <- remoteExamplesChangesApplier(adapterConfig, todos)
        // _            <- ZIO.logInfo(s"${adapterConfig.targetName} : Build examples summary")
        overviewOption <- Overview.makeOverview(remoteExamples, adapterConfig, config)
        // _            <- ZIO.logInfo(s"${adapterConfig.targetName} : Publish examples summary")
        overviewTodo    = computeWorkToDo(overviewOption, remoteStates)
        remoteOverview <- remoteExamplesChangesApplier(adapterConfig, overviewTodo)
        _              <- RIO.foreach(remoteOverview.headOption)(publishedOverview => ZIO.logInfo(s"${adapterConfig.targetName} : Summary available at ${publishedOverview.state.url}"))
      } yield ()
    }
  }

  def examplesPublish(examples: Vector[CodeExample], config: CodeExampleManagerConfig): RIO[SttpClient, Unit] = {
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

  def countExamplesByPublishKeyword(examples: Vector[CodeExample]): Map[String, Int] = {
    examples
      .flatMap(example => example.publish.map(key => key -> example))
      .groupMap { case (key, _) => key } { case (_, ex) => ex }
      .map { case (key, examples) => key -> examples.size }
  }

  def synchronizeEffect: RIO[Clock & SttpClient & ApplicationConfig, Unit] = for {
    startTime <- Clock.nanoTime
    config    <- getConfig[ApplicationConfig].map(_.codeExamplesManagerConfig)
    metaInfo   = config.metaInfo
    version    = metaInfo.version
    appName    = metaInfo.name
    appCode    = metaInfo.code
    projectURL = metaInfo.projectURL
    _         <- ZIO.logInfo(s"$appName application is starting")
    _         <- ZIO.logInfo(s"$appCode version $version")
    _         <- ZIO.logInfo(s"$appCode project page $projectURL (with configuration documentation) ")
    examples  <- examplesCollect
    _         <- ZIO.logInfo(s"Found ${examples.size} available locally for synchronization purpose")
    _         <- ZIO.logInfo(s"Found ${examples.count(_.publish.size > 0)} distinct publishable examples")
    _         <- ZIO.logInfo("Available by publishing targets : " + countExamplesByPublishKeyword(examples).toList.sorted.map { case (k, n) => s"$k:$n" }.mkString(", "))
    _         <- examplesPublish(examples, config)
    endTime   <- Clock.nanoTime
    _         <- ZIO.logInfo(s"Code examples manager publishing operations took ${(endTime - startTime) / 1000000}ms")
  } yield ()

  def main(args: Array[String]): Unit = {
    val configLayer     = ZLayer.fromZIO(Configuration())
    val httpClientLayer = AsyncHttpClientZioBackend.layer()

//    val loggingLayer =
//      Logging.console(
//        logLevel = LogLevel.Info,
//        format = LogFormat.ColoredLogFormat()
//      ) >>> Logging.withRootLoggerName("Synchronize")

    Runtime.default.unsafeRun(synchronizeEffect.provide(System.live, Console.live, Clock.live, configLayer, httpClientLayer))
  }
}
