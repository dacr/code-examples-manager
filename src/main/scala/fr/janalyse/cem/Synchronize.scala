/*
 * Copyright 2022 David Crosson
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
import zio.stream.*
import zio.config.*
import zio.nio.file.*
import zio.nio.charset.Charset

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitOption, OpenOption, StandardOpenOption}
import java.util.UUID
import zio.nio.file.Files
import sttp.client3.asynchttpclient.zio.{AsyncHttpClientZioBackend, SttpClient}
import fr.janalyse.cem.model.*
import fr.janalyse.cem.model.WhatToDo.*

import scala.util.Success
import scala.util.matching.Regex

object Synchronize {
  type SearchRoot = Path

  def findExamplesFromSearchRoot(
    searchRoot: SearchRoot,
    searchOnlyRegex: Option[Regex],
    ignoreMaskRegex: Option[Regex]
  ): ZIO[FileSystemService, Throwable, List[Either[ExampleIssue, CodeExample]]] = {
    for {
      foundFiles <- FileSystemService.searchFiles(searchRoot, searchOnlyRegex, ignoreMaskRegex)
      examples   <- ZIO.foreach(foundFiles)(path => CodeExample.makeExample(path, searchRoot).either)
    } yield examples
  }

  def examplesValidSearchRoots(searchRootDirectories: String): RIO[Any, List[SearchRoot]] = {
    for {
      roots      <- Task(searchRootDirectories.split("""\s*,\s*""").toList.map(_.trim).map(r => Path(r)))
      validRoots <- ZIO.filter(roots)(root => Files.isDirectory(root))
    } yield validRoots
  }

  def examplesCollectFor(searchRoots: List[SearchRoot]): ZIO[ApplicationConfig & FileSystemService, ExampleIssue | Throwable, List[CodeExample]] = {
    for {
      examplesConfig      <- getConfig[ApplicationConfig].map(_.codeExamplesManagerConfig.examples)
      searchOnlyPattern   <- ZIO.attempt(examplesConfig.searchOnlyPatternRegex())
      searchIgnorePattern <- ZIO.attempt(examplesConfig.searchIgnoreMaskRegex())
      foundExamplesList   <- ZIO.foreach(searchRoots)(fromRoot => findExamplesFromSearchRoot(fromRoot, searchOnlyPattern, searchIgnorePattern))
      foundExamples        = foundExamplesList.flatten
      validExamples        = foundExamples.collect { case Right(example) => example }
      invalidExamples      = foundExamples.collect { case Left(example) => example }
      _                   <- ZIO.foreach(invalidExamples)(issue => ZIO.logWarning(issue.toString))
    } yield validExamples
  }

  def examplesCheckCoherency(examples: Iterable[CodeExample]): Task[Unit] = {
    val uuids      = examples.map(_.uuid)
    val duplicated = uuids.groupBy(u => u).filter { case (_, duplicated) => duplicated.size > 1 }.keys
    if (duplicated.nonEmpty)
      Task.fail(new Error("Found duplicated UUIDs : " + duplicated.mkString(",")))
    else Task.succeed(())
  }

  val examplesCollect:ZIO[ApplicationConfig & FileSystemService, ExampleIssue | Throwable, List[CodeExample]] = for {
    searchRootDirectories <- getConfig[ApplicationConfig].map(_.codeExamplesManagerConfig.examples.searchRootDirectories)
    searchRoots           <- examplesValidSearchRoots(searchRootDirectories)
    _                     <- ZIO.log(s"Searching examples in ${searchRoots.mkString(",")}")
    localExamples         <- examplesCollectFor(searchRoots)
    _                     <- examplesCheckCoherency(localExamples)
  } yield localExamples

  def computeWorkToDo(examples: Iterable[CodeExample], states: Iterable[RemoteExampleState]): List[WhatToDo] = {
    val statesByUUID   = states.map(state => state.uuid -> state).toMap
    val examplesByUUID = examples.map(example => example.uuid -> example).toMap
    val examplesUUIDs  = examplesByUUID.keys.toSet
    val examplesTriple = { // (exampleUUID, foundLocalExample, foundRemoteExampleState)
      examples
        .map(example => (example.uuid, Some(example), statesByUUID.get(example.uuid))) ++
        states
          .filterNot(state => examplesUUIDs.contains(state.uuid))
          .map(state => (state.uuid, examplesByUUID.get(state.uuid), Some(state)))
    }
    examplesTriple.toSet.toList.collect {
      case (uuid, None, Some(state))                                        => OrphanRemoteExample(uuid, state)
      case (uuid, Some(example), None)                                      => AddExample(uuid, example)
      case (uuid, Some(example), Some(state)) if example.hash == state.hash => KeepRemoteExample(uuid, example, state)
      case (uuid, Some(example), Some(state)) if example.hash != state.hash => UpdateRemoteExample(uuid, example, state)
      case (uuid, y, z)                                                     => UnsupportedOperation(uuid, y, z)
    }
  }

  def checkRemote(adapterConfig: PublishAdapterConfig)(todo: WhatToDo): RIO[Any, Unit] = {
    val overviewUUID = UUID.fromString(adapterConfig.overviewUUID)
    todo match {
      case UnsupportedOperation(uuidOption, exampleOption, stateOption) => ZIO.log(s"${adapterConfig.targetName} : Invalid input $uuidOption - $exampleOption - $stateOption")
      case OrphanRemoteExample(uuid, state) if uuid != overviewUUID     => ZIO.log(s"${adapterConfig.targetName} : Found orphan example $uuid - ${state.description} - ${state.url}")
      case _: OrphanRemoteExample                                       => ZIO.unit
      case _: KeepRemoteExample                                         => ZIO.unit
      case _: UpdateRemoteExample                                       => ZIO.unit
      case _: AddExample                                                => ZIO.unit
      case _: DeleteRemoteExample                                       => ZIO.unit
    }
  }

  def checkCoherency(adapterConfig: PublishAdapterConfig, todos: Iterable[WhatToDo]): RIO[Any, Unit] =
    RIO.foreach(todos)(checkRemote(adapterConfig)).unit

  def examplesPublishToGivenAdapter(
    examples: Iterable[CodeExample],
    adapterConfig: PublishAdapterConfig
  ): RIO[ApplicationConfig & SttpClient, Unit] = {
    val examplesToSynchronize = examples.filter(_.publish.contains(adapterConfig.activationKeyword))
    if (!adapterConfig.enabled || examplesToSynchronize.isEmpty || adapterConfig.token.isEmpty) RIO.unit
    else {
      for {
        remoteStates   <- RemoteOperations.remoteExampleStatesFetch(adapterConfig)
        _              <- ZIO.log(s"${adapterConfig.targetName} : Found ${remoteStates.size}  already published artifacts")
        _              <- ZIO.log(s"${adapterConfig.targetName} : Found ${examplesToSynchronize.size} synchronisable examples")
        todos           = computeWorkToDo(examplesToSynchronize, remoteStates)
        _              <- checkCoherency(adapterConfig, todos)
        remoteExamples <- RemoteOperations.remoteExamplesChangesApply(adapterConfig, todos)
        // _            <- ZIO.log(s"${adapterConfig.targetName} : Build examples summary")
        overviewOption <- Overview.makeOverview(remoteExamples, adapterConfig)
        // _            <- ZIO.log(s"${adapterConfig.targetName} : Publish examples summary")
        overviewTodo    = computeWorkToDo(overviewOption, remoteStates)
        remoteOverview <- RemoteOperations.remoteExamplesChangesApply(adapterConfig, overviewTodo)
        _              <- RIO.foreach(remoteOverview.headOption)(publishedOverview => ZIO.log(s"${adapterConfig.targetName} : Summary available at ${publishedOverview.state.url}"))
      } yield ()
    }
  }

  def examplesPublish(examples: Iterable[CodeExample]): RIO[SttpClient & ApplicationConfig, Unit] = {
    for {
      adapters <- getConfig[ApplicationConfig].map(_.codeExamplesManagerConfig.publishAdapters)
      _        <- RIO.foreachPar(adapters.toList) { case (adapterName, adapterConfig) =>
                    examplesPublishToGivenAdapter(examples, adapterConfig)
                  }
    } yield ()
  }

  def countExamplesByPublishKeyword(examples: Iterable[CodeExample]): Map[String, Int] = {
    examples
      .flatMap(example => example.publish.map(key => key -> example))
      .groupMap { case (key, _) => key } { case (_, ex) => ex }
      .map { case (key, examples) => key -> examples.size }
  }

  def synchronizeEffect: ZIO[Clock & SttpClient & ApplicationConfig & FileSystemService, ExampleIssue | Throwable, Unit] = for {
    startTime <- Clock.nanoTime
    metaInfo  <- getConfig[ApplicationConfig].map(_.codeExamplesManagerConfig.metaInfo)
    version    = metaInfo.version
    appName    = metaInfo.name
    appCode    = metaInfo.code
    projectURL = metaInfo.projectURL
    _         <- ZIO.log(s"$appName application is starting")
    _         <- ZIO.log(s"$appCode version $version")
    _         <- ZIO.log(s"$appCode project page $projectURL (with configuration documentation) ")
    examples  <- examplesCollect
    _         <- ZIO.log(s"Found ${examples.size} available locally for synchronization purpose")
    _         <- ZIO.log(s"Found ${examples.count(_.publish.size > 0)} distinct publishable examples")
    _         <- ZIO.log("Available by publishing targets : " + countExamplesByPublishKeyword(examples).toList.sorted.map { case (k, n) => s"$k:$n" }.mkString(", "))
    _         <- examplesPublish(examples)
    endTime   <- Clock.nanoTime
    _         <- ZIO.log(s"Code examples manager publishing operations took ${(endTime - startTime) / 1000000}ms")
  } yield ()

}
