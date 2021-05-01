package fr.janalyse.cem

import fr.janalyse.cem.model.{AddExample, CodeExample, OrphanRemoteExample, IgnoreExample, KeepRemoteExample, RemoteExample, RemoteExampleState, UnsupportedOperation, UpdateRemoteExample, WhatToDo}
import fr.janalyse.cem.tools.DescriptionTools
import org.json4s.JValue
import sttp.client3.asynchttpclient.zio.SttpClient
import sttp.client3.json4s._
import zio.{RIO, Task, ZIO}
import zio.logging._
import sttp.client3._
import sttp.client3.asynchttpclient.zio._
import sttp.model.Uri


object RemoteGithubOperations {
  def githubInjectAuthToken[A, B](request: Request[A, B], tokenOption: Option[String]) = {
    val base = request.header("Accept", "application/vnd.github.v3+json")
    tokenOption.fold(base)(token => base.header("Authorization", s"token $token"))
  }

  implicit val formats = org.json4s.DefaultFormats
  implicit val serialization = org.json4s.jackson.Serialization

  case class GithubUser(
    login: String, // user name in APIs
    name: String,
    id: Int,
    public_gists: Int,
    private_gists: Int,
    followers: Int,
    following: Int,
  )

  case class GistFileInfo(
    filename: String,
    `type`: String,
    language: String,
    raw_url: String,
    size: Int,
  )

  case class GistInfo(
    id: String,
    description: String,
    html_url: String,
    public: Boolean,
    files: Map[String, GistFileInfo],
  )

  def githubUser(adapterConfig: PublishAdapterConfig): RIO[Logging with SttpClient, GithubUser] = {
    import adapterConfig.apiEndPoint
    for {
      apiURI <- uriParse(s"$apiEndPoint/user")
      query = basicRequest.get(apiURI).response(asJson[GithubUser])
      responseBody <- send(githubInjectAuthToken(query, adapterConfig.token)).map(_.body).absolve
    } yield responseBody
  }

  def uriParse(link: String): Task[Uri] = {
    RIO.fromEither(Uri.parse(link).swap.map(msg => new Error(msg)).swap)
  }


  def webLinkingExtractNext(link:String):Option[String] = {
    // Using Web Linking to get large amount of results : https://tools.ietf.org/html/rfc5988
    val nextLinkRE = """.*<([^>]+)>; rel="next".*""".r
    nextLinkRE.findFirstMatchIn(link).map(_.group(1))
  }

  def githubRemoteExamplesStatesFetch(adapterConfig: PublishAdapterConfig): RIO[Logging with SttpClient, Iterable[RemoteExampleState]] = {
    import adapterConfig.apiEndPoint

    def worker(uri: Uri): RIO[Logging with SttpClient, Iterable[GistInfo]] = {
      val query = basicRequest.get(uri).response(asJson[Vector[GistInfo]])
      for {
        response <- send(githubInjectAuthToken(query, adapterConfig.token))
        gists <- RIO.fromEither(response.body)
        nextLinkOption = response.header("Link").flatMap(webLinkingExtractNext)
        nextUriOption <- RIO.foreach(nextLinkOption)(link => uriParse(link))
        nextGists <- RIO.foreach(nextUriOption)(uri => worker(uri)).map(_.getOrElse(Iterable.empty))
      } yield gists ++ nextGists
    }

    for {
      userLogin <- githubUser(adapterConfig).map(_.login)
      count = 100
      link = s"$apiEndPoint/users/$userLogin/gists?page=1&per_page=$count"
      uri <- uriParse(link)
      gists <- worker(uri)
    } yield githubRemoteGistsToRemoteExampleState(gists)
  }

  def githubRemoteGistsToRemoteExampleState(gists: Iterable[GistInfo]): Iterable[RemoteExampleState] = {
    for {
      gist <- gists
      desc = gist.description
      (uuid, checksum) <- DescriptionTools.extractMetaDataFromDescription(desc)
      url = gist.html_url
      files = gist.files
    } yield {
      RemoteExampleState(
        remoteId = gist.id,
        description = desc,
        url = url,
        filename = files.keys.headOption, // TODO
        uuid = uuid,
        checksum = checksum,
      )
    }
  }

  def githubRemoteExampleAdd(adapterConfig: PublishAdapterConfig, addExample: AddExample): RIO[Logging with SttpClient, RemoteExample] = {
    def requestBody(description: String) = Map(
      "description" -> description,
      "public" -> true,
      "files" -> Map(
        addExample.example.filename -> Map(
          "filename" -> addExample.example.filename,
          "content" -> addExample.example.content
        )
      )
    )

    for {
      apiURI <- uriParse(s"${adapterConfig.apiEndPoint}/gists")
      example = addExample.example
      description <- ZIO.getOrFail(DescriptionTools.makeDescription(example))
      query = basicRequest.post(apiURI).body(requestBody(description)).response(asJson[JValue])
      response <- send(githubInjectAuthToken(query, adapterConfig.token)).map(_.body).absolve
      id <- ZIO.getOrFail((response \ "id").extractOpt[String])
      url <- ZIO.getOrFail((response \ "html_url").extractOpt[String])
      _ <- log.info(s"""${adapterConfig.targetName} : ADDED $id - ${example.summary.getOrElse("")} - $url""")
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


  def githubRemoteExampleUpdate(adapterConfig: PublishAdapterConfig, update: UpdateRemoteExample): RIO[Logging with SttpClient, RemoteExample] = {
    def requestBody(description: String) = Map(
      "description" -> description,
      "files" -> Map(
        update.state.filename.getOrElse(update.example.filename) -> Map(
          "filename" -> update.example.filename,
          "content" -> update.example.content
        )
      )
    )
    val gistId = update.state.remoteId
    for {
      apiURI <- uriParse(s"${adapterConfig.apiEndPoint}/gists/$gistId")
      example = update.example
      description <- ZIO.getOrFail(DescriptionTools.makeDescription(example))
      query = basicRequest.post(apiURI).body(requestBody(description)).response(asJson[JValue])
      authedQuery = githubInjectAuthToken(query, adapterConfig.token)
      response <- send(authedQuery).map(_.body).absolve
      id <- ZIO.getOrFail((response \ "id").extractOpt[String])
      url <- ZIO.getOrFail((response \ "html_url").extractOpt[String])
      _ <- log.info(s"""${adapterConfig.targetName} : UPDATED $id - ${example.summary.getOrElse("")} - $url""")
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


  def githubRemoteExampleChangesApply(adapterConfig: PublishAdapterConfig)(todo: WhatToDo): RIO[Logging with SttpClient, Option[RemoteExample]] = {
    todo match {
      case _: IgnoreExample => ZIO.succeed(None)
      case _: UnsupportedOperation => ZIO.succeed(None)
      case _: OrphanRemoteExample => ZIO.succeed(None)
      case KeepRemoteExample(uuid, example, state) => ZIO.succeed(Some(RemoteExample(example, state)))
      case exampleTODO: UpdateRemoteExample => githubRemoteExampleUpdate(adapterConfig, exampleTODO).asSome
      case exampleTODO: AddExample => githubRemoteExampleAdd(adapterConfig, exampleTODO).asSome
    }
  }

  def githubRemoteExamplesChangesApply(adapterConfig: PublishAdapterConfig, todos: Iterable[WhatToDo]): RIO[Logging with SttpClient, Iterable[RemoteExample]] = {
    for {
      remotes <- RIO.foreach(todos)(githubRemoteExampleChangesApply(adapterConfig)).map(_.flatten)
    } yield remotes
  }

}
