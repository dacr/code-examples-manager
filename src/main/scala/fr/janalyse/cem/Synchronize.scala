/*
 * Copyright 2023 David Crosson
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
import zio.lmdb.LMDB

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitOption, OpenOption, StandardOpenOption}
import java.util.UUID
import zio.nio.file.Files
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import sttp.client3.SttpBackend
import fr.janalyse.cem.model.*
import fr.janalyse.cem.model.WhatToDo.*

import scala.util.Success
import scala.util.matching.Regex

object Synchronize {
  type SttpClient = SttpBackend[Task, Any]
  type SearchRoot = Path

  def findExamplesFromSearchRoot(
    searchRoot: SearchRoot,
    searchOnlyRegex: Option[Regex],
    ignoreMaskRegex: Option[Regex]
  ): ZIO[FileSystemService & LMDB, Throwable, List[Either[ExampleIssue, CodeExample]]] = {
    for {
      foundFiles <- FileSystemService.searchFiles(searchRoot, searchOnlyRegex, ignoreMaskRegex)
      examples   <- ZIO.foreach(foundFiles)(path => CodeExample.buildFromFile(path, searchRoot).either)
    } yield examples
  }

  def examplesValidSearchRoots(searchRootDirectories: String): RIO[Any, List[SearchRoot]] = {
    for {
      roots      <- ZIO.attempt(searchRootDirectories.split("""\s*,\s*""").toList.map(_.trim).map(r => Path(r)))
      validRoots <- ZIO.filter(roots)(root => Files.isDirectory(root))
    } yield validRoots
  }

  def examplesCollectFor(searchRoots: List[SearchRoot]): ZIO[FileSystemService & LMDB, ExampleIssue | Throwable, List[CodeExample]] = {
    for {
      examplesConfig      <- ZIO.config(ApplicationConfig.config).map(_.codeExamplesManagerConfig.examples)
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
      ZIO.fail(new Error("Found duplicated UUIDs : " + duplicated.mkString(",")))
    else ZIO.succeed(())
  }

  val examplesCollect: ZIO[FileSystemService & LMDB, ExampleIssue | Throwable, List[CodeExample]] = for {
    searchRootDirectories <- ZIO.config(ApplicationConfig.config).map(_.codeExamplesManagerConfig.examples.searchRootDirectories)
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
    ZIO.foreach(todos)(checkRemote(adapterConfig)).unit

  def examplesPublishToGivenAdapter(
    examples: Iterable[CodeExample],
    adapterConfig: PublishAdapterConfig
  ): RIO[SttpClient, Unit] = {
    val examplesToSynchronize = examples.filter(_.publish.contains(adapterConfig.activationKeyword))
    if (!adapterConfig.enabled || examplesToSynchronize.isEmpty || adapterConfig.token.isEmpty) ZIO.unit
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
        _              <- ZIO.foreach(remoteOverview.headOption)(publishedOverview => ZIO.log(s"${adapterConfig.targetName} : Summary available at ${publishedOverview.state.url}"))
      } yield ()
    }
  }

  def examplesPublish(examples: Iterable[CodeExample]): RIO[SttpClient, Unit] = {
    for {
      adapters <- ZIO.config(ApplicationConfig.config).map(_.codeExamplesManagerConfig.publishAdapters)
      _        <- ZIO.foreachPar(adapters.toList) { case (adapterName, adapterConfig) =>
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

  def statsEffect(examples: List[CodeExample]) =
    for {
      metaInfo     <- ZIO.config(ApplicationConfig.config).map(_.codeExamplesManagerConfig.metaInfo)
      version       = metaInfo.version
      appCode       = metaInfo.code
      appName       = metaInfo.name
      oldestCreated = examples.filter(_.createdOn.isDefined).minBy(_.createdOn)
      latestCreated = examples.filter(_.createdOn.isDefined).maxBy(_.createdOn)
      latestUpdated = examples.filter(_.lastUpdated.isDefined).maxBy(_.lastUpdated)
      message       =
        s"""$appCode $appName version $version
           |Found ${examples.size} available locally for synchronization purpose
           |Found ${examples.count(_.publish.size > 0)} distinct publishable examples
           |Oldest example : ${oldestCreated.createdOn.get} ${oldestCreated.filename}
           |Latest example : ${latestCreated.createdOn.get} ${latestCreated.filename}
           |Latest updated : ${latestUpdated.lastUpdated.get} ${latestUpdated.filename}
           |Available by publishing targets : ${countExamplesByPublishKeyword(examples).toList.sorted.map { case (k, n) => s"$k:$n" }.mkString(", ")}
           |Defined keywords count : ${examples.flatMap(_.keywords).toSet.size}
           |Defined keywords : ${examples.flatMap(_.keywords).distinct.sorted.mkString(",")}
           |""".stripMargin
    } yield message

  val versionEffect =
    for {
      metaInfo  <- ZIO.config(ApplicationConfig.config).map(_.codeExamplesManagerConfig.metaInfo)
      version    = metaInfo.version
      appName    = metaInfo.name
      appCode    = metaInfo.code
      projectURL = metaInfo.projectURL
      message    =
        s"""$appCode $appName version $version
           |$appCode project page $projectURL
           |$appCode contact email = ${metaInfo.contactEmail}
           |$appCode build Version = ${metaInfo.buildVersion}
           |$appCode build DateTime = ${metaInfo.buildDateTime}
           |$appCode build UUID = ${metaInfo.buildUUID}
           |""".stripMargin
    } yield message

  def synchronizeEffect: ZIO[SttpClient & FileSystemService & LMDB, ExampleIssue | Throwable, Unit] = for {
    startTime <- Clock.nanoTime
    metaInfo  <- ZIO.config(ApplicationConfig.config).map(_.codeExamplesManagerConfig.metaInfo)
    appName    = metaInfo.name
    version   <- versionEffect
    _         <- ZIO.log(s"\n$version")
    examples  <- examplesCollect
    stats     <- statsEffect(examples)
    _         <- ZIO.log(s"\n$stats")
    _         <- examplesPublish(examples)
    endTime   <- Clock.nanoTime
    _         <- ZIO.log(s"$appName publishing operations took ${(endTime - startTime) / 1000000}ms")
  } yield ()

}
