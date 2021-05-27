package fr.janalyse.cem

import fr.janalyse.cem.model.*
import fr.janalyse.cem.model.WhatToDo.*
import fr.janalyse.cem.tools.DescriptionTools
import fr.janalyse.cem.tools.DescriptionTools.remoteExampleFileRename
import fr.janalyse.cem.tools.HttpTools.{uriParse, webLinkingExtractNext}
import sttp.client3.asynchttpclient.zio.SttpClient
import sttp.client3.circe.*
import zio.{RIO, Task, ZIO}
import zio.logging.*
import sttp.model.Uri
import sttp.client3.*
import sttp.client3.asynchttpclient.zio.*
import io.circe.Codec
import sttp.client3.circe.circeBodySerializer

import java.time.OffsetDateTime


object RemoteGitlabOperations {

  case class SnippetAuthor(
    id: Long,
    name: String,
    username: String,
    state: String,
    avatar_url: String,
    web_url: String
  ) derives Codec.AsObject

  case class SnippetInfo(
    id: Long,
    title: String,
    file_name: String,
    description: String,
    visibility: String,
    author: SnippetAuthor,
    updated_at: OffsetDateTime,
    created_at: OffsetDateTime,
    web_url: String,
    raw_url: String,
  ) derives Codec.AsObject

  case class SnippetAddRequest(
    title: Option[String],
    file_name: String,
    content: String,
    description: String,
    visibility: String
  ) derives Codec.AsObject

  case class SnippetUpdateRequest(
    id: String,
    title: Option[String],
    file_name: String,
    content: String,
    description: String,
    visibility: String,
  ) derives Codec.AsObject

  /*
    given Codec[SnippetAuthor] = Codec.AsObject.derived

    given Codec[SnippetInfo] = Codec.AsObject.derived

    given Codec[SnippetAddRequest] = Codec.AsObject.derived

    given Codec[SnippetUpdateRequest] = Codec.AsObject.derived
   */

  def gitlabInjectAuthToken[A, B](request: Request[A, B], tokenOption: Option[String]) = {
    val base = request.header("Content-Type", "application/json")
    tokenOption.fold(base)(token => base.header("Authorization", s"Bearer $token"))
  }


  def gitlabRemoteExamplesStatesFetch(adapterConfig: PublishAdapterConfig): RIO[Logging with SttpClient, Iterable[RemoteExampleState]] = {
    import adapterConfig.apiEndPoint

    def worker(uri: Uri): RIO[Logging with SttpClient, Iterable[SnippetInfo]] = {
      val query = basicRequest.get(uri).response(asJson[Vector[SnippetInfo]])
      for {
        _ <- log.info(s"${adapterConfig.targetName} : Fetching from $uri")
        response <- send(gitlabInjectAuthToken(query, adapterConfig.token))
        snippets <- RIO.fromEither(response.body)
        nextLinkOption = response.header("Link").flatMap(webLinkingExtractNext)
        nextUriOption <- RIO.foreach(nextLinkOption)(link => uriParse(link))
        nextSnippets <- RIO.foreach(nextUriOption)(uri => worker(uri)).map(_.getOrElse(Iterable.empty))
      } yield snippets ++ nextSnippets
    }

    val perPage = 100
    for {
      uri <- uriParse(s"$apiEndPoint/snippets?page=1&per_page=$perPage")
      snippets <- worker(uri)
    } yield gitlabRemoteGistsToRemoteExampleState(snippets)
  }

  def gitlabRemoteGistsToRemoteExampleState(snippets: Iterable[SnippetInfo]): Iterable[RemoteExampleState] = {
    for {
      snippet <- snippets
      desc = snippet.description
      (uuid, checksum) <- DescriptionTools.extractMetaDataFromDescription(desc)
      url = snippet.web_url
      filename = snippet.file_name
    } yield {
      RemoteExampleState(
        remoteId = snippet.id.toString,
        description = desc,
        url = url,
        filename = Some(filename),
        uuid = uuid,
        checksum = checksum,
      )
    }
  }

  def gitlabRemoteExampleAdd(adapterConfig: PublishAdapterConfig, todo: AddExample): RIO[Logging with SttpClient, RemoteExample] = {

    def requestBody(description: String): SnippetAddRequest = SnippetAddRequest(
      title = todo.example.summary,
      file_name = remoteExampleFileRename(todo.example.filename, adapterConfig),
      content = todo.example.content,
      description = description,
      visibility = adapterConfig.defaultVisibility.getOrElse("public")
    )

    for {
      apiURI <- uriParse(s"${adapterConfig.apiEndPoint}/snippets")
      example = todo.example
      description <- ZIO.getOrFail(DescriptionTools.makeDescription(example))
      query = basicRequest.post(apiURI).body(requestBody(description)).response(asJson[SnippetInfo])
      response <- send(gitlabInjectAuthToken(query, adapterConfig.token)).map(_.body).absolve
      id = response.id.toString
      url = response.web_url
      _ <- log.info(s"""${adapterConfig.targetName} : ADDED ${todo.uuid} - ${example.summary.getOrElse("")} - $url""")
    } yield RemoteExample(
      todo.example,
      RemoteExampleState(
        remoteId = id,
        description = description,
        url = url,
        filename = Some(todo.example.filename),
        uuid = todo.uuid,
        checksum = example.checksum,
      ))
  }

  def gitlabRemoteExampleUpdate(adapterConfig: PublishAdapterConfig, todo: UpdateRemoteExample): RIO[Logging with SttpClient, RemoteExample] = {
    def requestBody(description: String): SnippetUpdateRequest = SnippetUpdateRequest(
      id = todo.state.remoteId,
      title = todo.example.summary,
      file_name = remoteExampleFileRename(todo.example.filename, adapterConfig),
      content = todo.example.content,
      description = description,
      visibility = adapterConfig.defaultVisibility.getOrElse("public")
    )

    val snippetId = todo.state.remoteId
    for {
      apiURI <- uriParse(s"${adapterConfig.apiEndPoint}/snippets/$snippetId")
      example = todo.example
      description <- ZIO.getOrFail(DescriptionTools.makeDescription(example))
      query = basicRequest.put(apiURI).body(requestBody(description)).response(asJson[SnippetInfo])
      authedQuery = gitlabInjectAuthToken(query, adapterConfig.token)
      response <- send(authedQuery).map(_.body).absolve
      id = response.id.toString
      url = response.web_url
      _ <- log.info(s"""${adapterConfig.targetName} : UPDATED ${todo.uuid} - ${example.summary.getOrElse("")} - $url""")
    } yield RemoteExample(
      todo.example,
      RemoteExampleState(
        remoteId = id,
        description = description,
        url = url,
        filename = Some(todo.example.filename),
        uuid = todo.uuid,
        checksum = example.checksum,
      ))
  }

  def gitlabRemoteExampleChangesApply(adapterConfig: PublishAdapterConfig)(todo: WhatToDo): RIO[Logging with SttpClient, Option[RemoteExample]] = {
    todo match {
      case _: IgnoreExample => ZIO.succeed(None)
      case _: UnsupportedOperation => ZIO.succeed(None)
      case _: OrphanRemoteExample => ZIO.succeed(None)
      case KeepRemoteExample(uuid, example, state) => ZIO.succeed(Some(RemoteExample(example, state)))
      case exampleTODO: UpdateRemoteExample => gitlabRemoteExampleUpdate(adapterConfig, exampleTODO).asSome
      case exampleTODO: AddExample => gitlabRemoteExampleAdd(adapterConfig, exampleTODO).asSome
    }
  }

  def gitlabRemoteExamplesChangesApply(adapterConfig: PublishAdapterConfig, todos: Iterable[WhatToDo]): RIO[Logging with SttpClient, Iterable[RemoteExample]] = {
    for {
      remotes <- RIO.foreach(todos)(gitlabRemoteExampleChangesApply(adapterConfig)).map(_.flatten)
    } yield remotes
  }

}
