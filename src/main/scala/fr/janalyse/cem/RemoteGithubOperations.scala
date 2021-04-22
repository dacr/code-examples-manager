package fr.janalyse.cem

import caliban.client.Operations.RootQuery
import caliban.client.SelectionBuilder
import fr.janalyse.cem.model.{AddExample, CodeExample, DeleteRemoteExample, IgnoreExample, KeepRemoteExample, RemoteExample, RemoteExampleState, UnsupportedOperation, UpdateRemoteExample, WhatToDo}
import fr.janalyse.cem.tools.DescriptionTools
import org.json4s.JValue
import sttp.client3.asynchttpclient.zio.SttpClient
import sttp.client3.json4s._
import zio.{RIO, Task, ZIO}
import zio.logging._
import sttp.client3._
import sttp.client3.asynchttpclient.zio._
import sttp.model.Uri

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.Base64


object RemoteGithubOperations {
  def githubInjectAuthToken[A, B](request: Request[A, B], tokenOption: Option[String]) = {
    val base = request.header("Accept", "application/vnd.github.v3+json")
    tokenOption.fold(base)(token => base.header("Authorization", s"token $token"))
  }

  case class RemoteGist(id: String, description: Option[String], url: String, files:Option[List[String]])

  case class RemoteGistConnection(gists: List[RemoteGist], totalCount: Int, pagination: RemoteGistPagination)

  case class RemoteGistPagination(endCursor: Option[String], hasNextPage: Boolean)

  def gistQuery(after: Option[String] = None, first: Option[Int] = Some(100)): SelectionBuilder[RootQuery, RemoteGistConnection] = {
    import fr.janalyse.cem.graphql.github.Client._
    import fr.janalyse.cem.graphql.github.Client.PageInfo._
    import fr.janalyse.cem.graphql.github.Client.User.gists
    import fr.janalyse.cem.graphql.github.Client.Query.viewer
    import fr.janalyse.cem.graphql.github.Client.GistConnection._

    viewer(
      gists(first = first, after = after, orderBy = Some(GistOrder(direction = OrderDirection.DESC, field = GistOrderField.CREATED_AT)))(
        (
          nodes(
            (Gist.id ~
              Gist.description ~
              Gist.url ~
              Gist.files()(GistFile.name)
              ).map{ case (((id, desc), url), files) => RemoteGist(id, desc, url, files.map(_.flatten.flatten))}
          ) ~
            totalCount ~
            pageInfo((endCursor ~ hasNextPage).mapN(RemoteGistPagination))
          ).map { case ((Some(a), b), c) => RemoteGistConnection(a.flatten, b, c) }
      )
    )
  }


  def githubRemoteGistsToRemoteExampleState(gists: List[RemoteGist]): List[RemoteExampleState] = {
    for {
      gist <- gists
      desc <- gist.description
      (uuid, checksum) <- DescriptionTools.extractMetaDataFromDescription(desc)
      url = gist.url
      filename <- gist.files
    } yield RemoteExampleState(
      remoteId = gist.id,
      description = desc,
      url = url,
      filename = filename.headOption,
      uuid = uuid,
      checksum = checksum,
    )
  }

  def githubRemoteExamplesStatesFetch(adapterConfig: PublishAdapterConfig, after: Option[String] = None): RIO[Logging with SttpClient, List[RemoteExampleState]] = {
    val uriEither = Uri.parse(adapterConfig.graphqlEndPoint).swap.map(msg => new Error(msg)).swap
    for {
      apiURI <- RIO.fromEither(uriEither)
      query = gistQuery(after).toRequest(apiURI, useVariables = true)
      response <- send(githubInjectAuthToken(query, adapterConfig.token)).map(_.body).absolve
      nextResults <-
        if (response.pagination.hasNextPage) githubRemoteExamplesStatesFetch(adapterConfig, response.pagination.endCursor)
        else RIO.succeed(List.empty[RemoteExampleState])
    } yield githubRemoteGistsToRemoteExampleState(response.gists) ::: nextResults
  }

  implicit val formats = org.json4s.DefaultFormats
  implicit val serialization = org.json4s.jackson.Serialization

  def githubRemoteExampleAdd(adapterConfig:PublishAdapterConfig, addExample:AddExample):ZIO[Logging with SttpClient, Option[Throwable],RemoteExample] = {
    import adapterConfig.apiEndPoint
    val uriEither = Uri.parse(s"$apiEndPoint/gists").swap.map(msg => new Error(msg)).swap
    def requestBody(description:String) = Map(
      "description"-> description,
      "public"->true,
      "files"->Map(
        addExample.example.filename->Map(
          "filename"->addExample.example.filename,
          "content"->addExample.example.content
        )
      )
    )
    for {
      apiURI <- RIO.fromEither(uriEither).asSomeError
      example = addExample.example
      description <- ZIO.getOrFail(DescriptionTools.makeDescription(example)).asSomeError
      query = basicRequest.post(apiURI).body(requestBody(description)).response(asJson[JValue])
      response <- send(githubInjectAuthToken(query, adapterConfig.token)).map(_.body).absolve.asSomeError
      id <- ZIO.getOrFail( (response \ "id").extractOpt[String] ).asSomeError
      url <- ZIO.getOrFail( (response \ "html_url").extractOpt[String] ).asSomeError
      _ <- log.info(s"""ADDED $id - ${example.summary.getOrElse("")} - $url""")
    } yield RemoteExample(
      addExample.example,
      RemoteExampleState(
        remoteId = id,
        description = description,
        url = url,
        filename = Some(addExample.example.filename),
        uuid = addExample.uuid,
        checksum = example.checksum,
      ))
  }


  // MDQ6R2lzdDQ1NTk5OTQ= --> 04:Gist4559994 --> 4559994
  def decodeGistId(codedId: String):Task[String] = {
    def b64decode(input:String, charsetName:String="UTF-8"):String = {
      val bytes = Base64.getDecoder.decode(input.getBytes("US-ASCII"))
      Charset.forName(charsetName).decode(ByteBuffer.wrap(bytes)).toString
    }

    for {
      decoded <- Task.effect(b64decode(codedId))
      _ <- Task.cond(decoded.startsWith("04:Gist"), decoded, new Exception("Not supported gist identifier codec"))
      idOption = decoded.split(":Gist",2).drop(1).headOption
      id <- Task.getOrFail(idOption)
    } yield id
  }

  def githubRemoteExampleUpdate(adapterConfig:PublishAdapterConfig, update:UpdateRemoteExample):ZIO[Logging with SttpClient, Option[Throwable],RemoteExample] = {
    def requestBody(description:String) = Map(
      "description"-> description,
      "files"->Map(
        update.state.filename.getOrElse(update.example.filename) -> Map(
          "filename"->update.example.filename,
          "content"->update.example.content
        )
      )
    )
    for {
      gistId <- decodeGistId(update.state.remoteId).asSomeError
      apiEndPoint =  adapterConfig.apiEndPoint
      uriEither = Uri.parse(s"$apiEndPoint/gists/$gistId").swap.map(msg => new Error(msg)).swap
      apiURI <- RIO.fromEither(uriEither).asSomeError
      example = update.example
      description <- ZIO.getOrFail(DescriptionTools.makeDescription(example)).asSomeError
      query = basicRequest.post(apiURI).body(requestBody(description)).response(asJson[JValue])
      authedQuery = githubInjectAuthToken(query, adapterConfig.token)
      response <- send(authedQuery).map(_.body).absolve.asSomeError
      id <- ZIO.getOrFail( (response \ "id").extractOpt[String] ).asSomeError
      url <- ZIO.getOrFail( (response \ "html_url").extractOpt[String] ).asSomeError
      _ <- log.info(s"""UPDATED $id - ${example.summary.getOrElse("")} - $url""")
    } yield RemoteExample(
      update.example,
      RemoteExampleState(
        remoteId = id,
        description = description,
        url = url,
        filename = Some(update.example.filename),
        uuid = update.uuid,
        checksum = example.checksum,
      ))
  }



  def githubRemoteExampleChangesApply(adapterConfig: PublishAdapterConfig)(todo: WhatToDo) : ZIO[Logging with SttpClient, Option[Throwable],Option[RemoteExample]] = {
    todo match {
      case IgnoreExample(example) => ZIO.succeed(None)
      case UnsupportedOperation(uuidOption, exampleOption, stateOption) => ZIO.succeed(None)
      case DeleteRemoteExample(uuid, state) =>
        for {
          _ <- log.info(s"Found orphan example : $uuid - ${state.description} - ${state.url}")
        } yield None
      case KeepRemoteExample(uuid, example, state) => ZIO.succeed(Some(RemoteExample(example, state)))
      //case UpdateRemoteExample(uuid, example, state) => ZIO.succeed(Some(RemoteExample(example, state)))
      case exampleTODO:UpdateRemoteExample => githubRemoteExampleUpdate(adapterConfig, exampleTODO).asSome
      //case AddExample(uuid, example) => ZIO.succeed(None)
      case exampleTODO:AddExample => githubRemoteExampleAdd(adapterConfig, exampleTODO).asSome
    }
  }

  def githubRemoteExamplesChangesApply(adapterConfig: PublishAdapterConfig, todos: Iterable[WhatToDo]) : RIO[Logging with SttpClient, Iterable[RemoteExample]] = {
    for {
      remotes <- RIO.collect(todos)(githubRemoteExampleChangesApply(adapterConfig)).map(_.flatten)
    } yield remotes
  }

}
