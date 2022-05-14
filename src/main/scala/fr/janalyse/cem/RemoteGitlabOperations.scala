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
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}
import sttp.model.Uri
import sttp.client3.*
import sttp.client3.ziojson.*
import sttp.client3.asynchttpclient.zio.SttpClient
import sttp.client3.asynchttpclient.zio.*
import java.util.UUID

import fr.janalyse.cem.model.*
import fr.janalyse.cem.model.WhatToDo.*
import fr.janalyse.cem.tools.DescriptionTools
import fr.janalyse.cem.tools.DescriptionTools.remoteExampleFileRename
import fr.janalyse.cem.tools.HttpTools.{uriParse, webLinkingExtractNext}

import java.time.OffsetDateTime

object RemoteGitlabOperations {

  case class SnippetAuthor(
    id: Long,
    name: String,
    username: String,
    state: String,
    avatar_url: String,
    web_url: String
  )

  object SnippetAuthor {
    implicit val decoder: JsonDecoder[SnippetAuthor] = DeriveJsonDecoder.gen
    implicit val encoder: JsonEncoder[SnippetAuthor] = DeriveJsonEncoder.gen
  }

  case class SnippetFileInfo(
    path: String,
    raw_url: String
  )

  object SnippetFileInfo {
    implicit val decoder: JsonDecoder[SnippetFileInfo] = DeriveJsonDecoder.gen
    implicit val encoder: JsonEncoder[SnippetFileInfo] = DeriveJsonEncoder.gen
  }

  case class SnippetInfo(
    id: Long,
    title: String,
    file_name: String,
    files: List[SnippetFileInfo],
    description: String,
    visibility: String,
    author: SnippetAuthor,
    updated_at: OffsetDateTime,
    created_at: OffsetDateTime,
    web_url: String,
    raw_url: String
  )
  object SnippetInfo     {
    implicit val decoder: JsonDecoder[SnippetInfo] = DeriveJsonDecoder.gen
    implicit val encoder: JsonEncoder[SnippetInfo] = DeriveJsonEncoder.gen
  }

  case class SnippetFileAdd(
    file_path: String,
    content: String
  )

  object SnippetFileAdd {
    implicit val decoder: JsonDecoder[SnippetFileAdd] = DeriveJsonDecoder.gen
    implicit val encoder: JsonEncoder[SnippetFileAdd] = DeriveJsonEncoder.gen
  }

  case class SnippetAddRequest(
    title: Option[String],
    description: String,
    visibility: String,
    files: List[SnippetFileAdd]
  )

  object SnippetAddRequest {
    implicit val decoder: JsonDecoder[SnippetAddRequest] = DeriveJsonDecoder.gen
    implicit val encoder: JsonEncoder[SnippetAddRequest] = DeriveJsonEncoder.gen
  }

  case class SnippetFileChange(
    action: String, // "create" | "update" | "delete" | "move"
    file_path: Option[String],
    previous_path: Option[String],
    content: Option[String]
  )

  object SnippetFileChange {
    implicit val decoder: JsonDecoder[SnippetFileChange] = DeriveJsonDecoder.gen
    implicit val encoder: JsonEncoder[SnippetFileChange] = DeriveJsonEncoder.gen
  }

  case class SnippetUpdateRequest(
    id: String,
    title: Option[String],
    description: String,
    visibility: String,
    files: List[SnippetFileChange]
  )

  object SnippetUpdateRequest {
    implicit val decoder: JsonDecoder[SnippetUpdateRequest] = DeriveJsonDecoder.gen
    implicit val encoder: JsonEncoder[SnippetUpdateRequest] = DeriveJsonEncoder.gen
  }

  def gitlabInjectAuthToken[A, B](request: Request[A, B], tokenOption: Option[String]) = {
    val base = request.header("Content-Type", "application/json")
    tokenOption.fold(base)(token => base.header("Authorization", s"Bearer $token"))
  }

  def gitlabRemoteExamplesStatesFetch(adapterConfig: PublishAdapterConfig): RIO[SttpClient & Clock & Random, Iterable[RemoteExampleState]] = {
    import adapterConfig.apiEndPoint

    def worker(uri: Uri): RIO[SttpClient, Iterable[SnippetInfo]] = {
      val query = basicRequest.get(uri).response(asJson[Vector[SnippetInfo]])
      for {
        _             <- ZIO.log(s"${adapterConfig.targetName} : Fetching from $uri")
        response      <- send(gitlabInjectAuthToken(query, adapterConfig.token))
        snippets      <- RIO.fromEither(response.body)
        nextLinkOption = response.header("Link").flatMap(webLinkingExtractNext)
        nextUriOption <- RIO.foreach(nextLinkOption)(link => uriParse(link))
        nextSnippets  <- RIO.foreach(nextUriOption)(uri => worker(uri)).map(_.getOrElse(Iterable.empty))
      } yield snippets ++ nextSnippets
    }

    val perPage = 100
    for {
      uri      <- uriParse(s"$apiEndPoint/snippets?page=1&per_page=$perPage")
      snippets <- worker(uri).retry(Schedule.exponential(100.millis, 2).jittered && Schedule.recurs(6))
    } yield gitlabRemoteGistsToRemoteExampleState(snippets)
  }

  def gitlabRemoteGistsToRemoteExampleState(snippets: Iterable[SnippetInfo]): Iterable[RemoteExampleState] = {
    for {
      snippet      <- snippets
      desc          = snippet.description
      (uuid, hash) <- DescriptionTools.extractMetaDataFromDescription(desc)
      url           = snippet.web_url
      filename      = snippet.file_name
      files         = snippet.files
    } yield {
      RemoteExampleState(
        remoteId = snippet.id.toString,
        description = desc,
        url = url,
        files = files.map(_.path),
        uuid = UUID.fromString(uuid),
        hash = hash
      )
    }
  }

  def buildAddRequestBody(adapterConfig: PublishAdapterConfig, todo: AddExample, description: String): SnippetAddRequest = SnippetAddRequest(
    title = todo.example.summary,
    description = description,
    visibility = adapterConfig.defaultVisibility.getOrElse("public"),
    files = List(
      SnippetFileAdd(
        file_path = remoteExampleFileRename(todo.example.filename, adapterConfig),
        content = todo.example.content
      )
    ) ++ todo.example.attachments.map { case (filename, content) =>
      SnippetFileAdd(
        file_path = remoteExampleFileRename(filename, adapterConfig),
        content = content
      )
    }
  )

  def gitlabRemoteExampleAdd(adapterConfig: PublishAdapterConfig, todo: AddExample): RIO[SttpClient, RemoteExample] = {
    for {
      apiURI      <- uriParse(s"${adapterConfig.apiEndPoint}/snippets")
      example      = todo.example
      description <- ZIO.getOrFail(DescriptionTools.makeDescription(example))
      requestBody  = buildAddRequestBody(adapterConfig, todo, description)
      query        = basicRequest.post(apiURI).body(requestBody).response(asJson[SnippetInfo])
      response    <- send(gitlabInjectAuthToken(query, adapterConfig.token)).map(_.body).absolve
      id           = response.id.toString
      url          = response.web_url
      _           <- ZIO.log(s"""${adapterConfig.targetName} : ADDED ${todo.uuid} - ${example.summary.getOrElse("")} - $url""")
    } yield RemoteExample(
      todo.example,
      RemoteExampleState(
        remoteId = id,
        description = description,
        url = url,
        files = List(todo.example.filename) ++ todo.example.attachments.keys,
        uuid = todo.uuid,
        hash = example.hash
      )
    )
  }

  def buildUpdateRequestBody(adapterConfig: PublishAdapterConfig, todo: UpdateRemoteExample, description: String): SnippetUpdateRequest = {
    val remoteFiles        = todo.state.files.toSet
    val remoteMainFilename = remoteExampleFileRename(todo.example.filename, adapterConfig)
    val remoteOrphanFiles  = (remoteFiles - remoteMainFilename) -- (
      todo.example.attachments.keys.map(f => remoteExampleFileRename(f, adapterConfig))
    )

    val updatedFiles = List(
      SnippetFileChange(
        action = if (remoteFiles.contains(remoteMainFilename)) "update" else "create",
        file_path = Some(remoteMainFilename),
        previous_path = None,
        content = Some(todo.example.content)
      )
    ) ++ todo.example.attachments.map { case (filename, content) =>
      val remoteFilename = remoteExampleFileRename(filename, adapterConfig)
      SnippetFileChange(
        action = if (remoteFiles.contains(remoteFilename)) "update" else "create",
        file_path = Some(remoteFilename),
        previous_path = None,
        content = Some(content)
      )
    } ++ remoteOrphanFiles.map(name =>
      SnippetFileChange(
        action = "delete",
        file_path = Some(name),
        previous_path = None,
        content = None
      )
    )

    SnippetUpdateRequest(
      id = todo.state.remoteId,
      title = todo.example.summary,
      description = description,
      visibility = adapterConfig.defaultVisibility.getOrElse("public"),
      files = updatedFiles
    )
  }

  def gitlabRemoteExampleUpdate(adapterConfig: PublishAdapterConfig, todo: UpdateRemoteExample): RIO[SttpClient, RemoteExample] = {
    val snippetId = todo.state.remoteId
    for {
      apiURI      <- uriParse(s"${adapterConfig.apiEndPoint}/snippets/$snippetId")
      example      = todo.example
      description <- ZIO.getOrFail(DescriptionTools.makeDescription(example))
      requestBody  = buildUpdateRequestBody(adapterConfig, todo, description)
      query        = basicRequest.put(apiURI).body(requestBody).response(asJson[SnippetInfo])
      authedQuery  = gitlabInjectAuthToken(query, adapterConfig.token)
      response    <- send(authedQuery).map(_.body).absolve
      id           = response.id.toString
      url          = response.web_url
      _           <- ZIO.log(s"""${adapterConfig.targetName} : UPDATED ${todo.uuid} - ${example.summary.getOrElse("")} - $url""")
    } yield RemoteExample(
      todo.example,
      RemoteExampleState(
        remoteId = id,
        description = description,
        url = url,
        files = List(todo.example.filename) ++ todo.example.attachments.keys,
        uuid = todo.uuid,
        hash = example.hash
      )
    )
  }

  def gitlabRemoteExampleChangesApply(adapterConfig: PublishAdapterConfig)(todo: WhatToDo): RIO[SttpClient, Option[RemoteExample]] = {
    todo match {
      case _: UnsupportedOperation                 => ZIO.succeed(None)
      case _: OrphanRemoteExample                  => ZIO.succeed(None)
      case _: DeleteRemoteExample                  => ZIO.succeed(None) // TODO - Add support for delete operation
      case KeepRemoteExample(uuid, example, state) => ZIO.succeed(Some(RemoteExample(example, state)))
      case exampleTODO: UpdateRemoteExample        => gitlabRemoteExampleUpdate(adapterConfig, exampleTODO).asSome
      case exampleTODO: AddExample                 => gitlabRemoteExampleAdd(adapterConfig, exampleTODO).asSome
    }
  }

  def gitlabRemoteExamplesChangesApply(adapterConfig: PublishAdapterConfig, todos: Iterable[WhatToDo]): RIO[SttpClient, Iterable[RemoteExample]] = {
    RIO.foreach(todos)(gitlabRemoteExampleChangesApply(adapterConfig)).map(_.flatten)
  }

}
