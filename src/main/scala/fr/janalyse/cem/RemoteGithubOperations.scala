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
import zio.json.*
import zio.json.ast.{Json, JsonCursor}
import zio.logging.*
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

object RemoteGithubOperations {

  case class GithubUser(
    login: String, // user name in APIs
    name: String,
    id: Int,
    public_gists: Int,
    private_gists: Int,
    followers: Int,
    following: Int
  )
  object GithubUser         {
    implicit val decoder: JsonDecoder[GithubUser] = DeriveJsonDecoder.gen
    implicit val encoder: JsonEncoder[GithubUser] = DeriveJsonEncoder.gen
  }

  case class GistFileInfo(
    filename: String,
    `type`: String,
    language: Option[String],
    raw_url: String,
    size: Int
  )
  object GistFileInfo       {
    implicit val decoder: JsonDecoder[GistFileInfo] = DeriveJsonDecoder.gen
    implicit val encoder: JsonEncoder[GistFileInfo] = DeriveJsonEncoder.gen
  }

  case class GistInfo(
    id: String,
    description: String,
    html_url: String,
    public: Boolean,
    files: Map[String, GistFileInfo]
  )
  object GistInfo           {
    implicit val decoder: JsonDecoder[GistInfo] = DeriveJsonDecoder.gen
    implicit val encoder: JsonEncoder[GistInfo] = DeriveJsonEncoder.gen
  }

  case class GistCreateResponse(
    id: String,
    html_url: String
  )
  object GistCreateResponse {
    implicit val decoder: JsonDecoder[GistCreateResponse] = DeriveJsonDecoder.gen
    implicit val encoder: JsonEncoder[GistCreateResponse] = DeriveJsonEncoder.gen
  }

  case class GistUpdateResponse(
    id: String,
    html_url: String
  )
  object GistUpdateResponse {
    implicit val decoder: JsonDecoder[GistUpdateResponse] = DeriveJsonDecoder.gen
    implicit val encoder: JsonEncoder[GistUpdateResponse] = DeriveJsonEncoder.gen
  }

  def githubInjectAuthToken[A, B](request: Request[A, B], tokenOption: Option[String]) = {
    val base = request.header("Accept", "application/vnd.github.v3+json")
    tokenOption.fold(base)(token => base.header("Authorization", s"token $token"))
  }

  def githubUser(adapterConfig: PublishAdapterConfig): RIO[SttpClient, GithubUser] = {
    import adapterConfig.apiEndPoint
    for {
      apiURI       <- uriParse(s"$apiEndPoint/user")
      query         = basicRequest.get(apiURI).response(asJson[GithubUser])
      responseBody <- send(githubInjectAuthToken(query, adapterConfig.token)).map(_.body).absolve
    } yield responseBody
  }

  def githubRemoteExamplesStatesFetch(adapterConfig: PublishAdapterConfig): RIO[SttpClient, Iterable[RemoteExampleState]] = {

    def worker(uri: Uri): RIO[SttpClient, Iterable[GistInfo]] = {
      val query = basicRequest.get(uri).response(asJson[Vector[GistInfo]])
      for {
        _             <- ZIO.log(s"${adapterConfig.targetName} : Fetching from $uri")
        response      <- send(githubInjectAuthToken(query, adapterConfig.token))
        gists         <- RIO.fromEither(response.body)
        nextLinkOption = response.header("Link").flatMap(webLinkingExtractNext)
        nextUriOption <- RIO.foreach(nextLinkOption)(link => uriParse(link))
        nextGists     <- RIO.foreach(nextUriOption)(uri => worker(uri)).map(_.getOrElse(Iterable.empty))
      } yield gists ++ nextGists
    }

    val perPage = 100
    for {
      userLogin <- githubUser(adapterConfig).map(_.login)
      link       = s"${adapterConfig.apiEndPoint}/users/$userLogin/gists?page=1&per_page=$perPage"
      uri       <- uriParse(link)
      gists     <- worker(uri)
    } yield githubRemoteGistsToRemoteExampleState(gists)
  }

  def githubRemoteGistsToRemoteExampleState(gists: Iterable[GistInfo]): Iterable[RemoteExampleState] = {
    for {
      gist             <- gists
      desc              = gist.description
      (uuid, checksum) <- DescriptionTools.extractMetaDataFromDescription(desc)
      url               = gist.html_url
      files             = gist.files
    } yield {
      RemoteExampleState(
        remoteId = gist.id,
        description = desc,
        url = url,
        filename = files.keys.headOption, // TODO
        uuid = UUID.fromString(uuid),
        checksum = checksum
      )
    }
  }

  def githubRemoteExampleAdd(adapterConfig: PublishAdapterConfig, todo: AddExample): RIO[SttpClient, RemoteExample] = {
    def requestBody(description: String): Json = {
      import Json.*
      val filename   = remoteExampleFileRename(todo.example.filename, adapterConfig)
      val publicBool = adapterConfig.defaultVisibility.map(_.trim.toLowerCase == "public").getOrElse(true)
      Obj(
        "description" -> Str(description),
        "public"      -> Bool(publicBool),
        "files"       -> Obj(
          filename -> Obj(
            "filename" -> Str(filename),
            "content"  -> Str(todo.example.content)
          )
        )
      )
    }

    for {
      apiURI      <- uriParse(s"${adapterConfig.apiEndPoint}/gists")
      example      = todo.example
      description <- ZIO.getOrFail(DescriptionTools.makeDescription(example))
      query        = basicRequest.post(apiURI).body(requestBody(description)).response(asJson[GistCreateResponse])
      response    <- send(githubInjectAuthToken(query, adapterConfig.token)).map(_.body).absolve
      id           = response.id
      url          = response.html_url
      _           <- ZIO.log(s"""${adapterConfig.targetName} : ADDED ${todo.uuid} - ${example.summary.getOrElse("")} - $url""")
    } yield RemoteExample(
      todo.example,
      RemoteExampleState(
        remoteId = id,
        description = description,
        url = url,
        filename = Some(todo.example.filename),
        uuid = todo.uuid,
        checksum = example.checksum
      )
    )
  }

  def githubRemoteExampleUpdate(adapterConfig: PublishAdapterConfig, todo: UpdateRemoteExample): RIO[SttpClient, RemoteExample] = {
    def requestBody(description: String): Json = {
      import Json.*
      val filename    = remoteExampleFileRename(todo.example.filename, adapterConfig)
      val oldFilename = todo.state.filename.getOrElse(filename)
      Obj( // TODO - find a better way
        "description" -> Str(description),
        "files"       -> Obj(
          oldFilename -> Obj(
            "filename" -> Str(filename),
            "content"  -> Str(todo.example.content)
          )
        )
      )

    }

    val gistId = todo.state.remoteId
    for {
      apiURI      <- uriParse(s"${adapterConfig.apiEndPoint}/gists/$gistId")
      example      = todo.example
      description <- ZIO.getOrFail(DescriptionTools.makeDescription(example))
      query        = basicRequest.post(apiURI).body(requestBody(description)).response(asJson[GistUpdateResponse])
      authedQuery  = githubInjectAuthToken(query, adapterConfig.token)
      response    <- send(authedQuery).map(_.body).absolve
      id           = response.id
      url          = response.html_url
      _           <- ZIO.log(s"""${adapterConfig.targetName} : UPDATED ${todo.uuid} - ${example.summary.getOrElse("")} - $url""")
    } yield RemoteExample(
      todo.example,
      RemoteExampleState(
        remoteId = id,
        description = description,
        url = url,
        filename = Some(todo.example.filename),
        uuid = todo.uuid,
        checksum = example.checksum
      )
    )
  }

  def githubRemoteExampleChangesApply(adapterConfig: PublishAdapterConfig)(todo: WhatToDo): RIO[SttpClient, Option[RemoteExample]] = {
    todo match {
      case _: UnsupportedOperation                 => ZIO.succeed(None)
      case _: OrphanRemoteExample                  => ZIO.succeed(None)
      case _: DeleteRemoteExample                  => ZIO.succeed(None) // TODO - Add support for delete operation
      case KeepRemoteExample(uuid, example, state) => ZIO.succeed(Some(RemoteExample(example, state)))
      case exampleTODO: UpdateRemoteExample        => githubRemoteExampleUpdate(adapterConfig, exampleTODO).asSome
      case exampleTODO: AddExample                 => githubRemoteExampleAdd(adapterConfig, exampleTODO).asSome
    }
  }

  def githubRemoteExamplesChangesApply(adapterConfig: PublishAdapterConfig, todos: Iterable[WhatToDo]): RIO[SttpClient, Iterable[RemoteExample]] = {
    for {
      remotes <- RIO.foreach(todos)(githubRemoteExampleChangesApply(adapterConfig)).map(_.flatten)
    } yield remotes
  }

}
