package fr.janalyse.cem

import caliban.client.Operations.RootQuery
import caliban.client.SelectionBuilder
import fr.janalyse.cem.model._
import fr.janalyse.cem.tools.DescriptionTools
import sttp.client3.asynchttpclient.zio.SttpClient
import zio.{RIO, Task, ZIO}
import zio.logging._
import sttp.client3._
import sttp.client3.asynchttpclient.zio._
import sttp.model.Uri


object RemoteGitlabOperations {
  def gitlabInjectAuthToken[A,B](request:Request[A,B], tokenOption: Option[String]) = {
    val base = request.header("Content-Type","application/json")
    tokenOption.fold(base)(token => base.header("Authorization", s"Bearer $token"))
  }

  case class RemoteSnippet(id: String, description: Option[String], url: String, filename: Option[String])

  case class RemoteSnippetConnection(snippets: List[RemoteSnippet], pagination: RemoteSnippetPagination)

  case class RemoteSnippetPagination(endCursor: Option[String], hasNextPage: Boolean)

  def snippetQuery(after: Option[String] = None, first: Option[Int] = Some(100)): SelectionBuilder[RootQuery, Option[Option[RemoteSnippetConnection]]] = {
    import fr.janalyse.cem.graphql.gitlab.Client._
    import fr.janalyse.cem.graphql.gitlab.Client.PageInfo._
    import fr.janalyse.cem.graphql.gitlab.Client.Query._
    import fr.janalyse.cem.graphql.gitlab.Client.User.snippets
    import fr.janalyse.cem.graphql.gitlab.Client.SnippetConnection._

    currentUser(
      snippets(first=first, after = after)(
        (
          nodes((Snippet.id ~ Snippet.description ~ Snippet.webUrl ~ Snippet.fileName).mapN(RemoteSnippet)) ~
            pageInfo((endCursor ~ hasNextPage).mapN(RemoteSnippetPagination))
          ).map { case (Some(a), c) => RemoteSnippetConnection(a.flatten, c) }
      )
    )
  }

  def gitlabRemoteGistsToRemoteExampleState(snippets: List[RemoteSnippet]): List[RemoteExampleState] = {
    for {
      snippet <- snippets
      desc <- snippet.description
      (uuid, checksum) <- DescriptionTools.extractMetaDataFromDescription(desc)
      url = snippet.url
      filename = snippet.filename
    } yield RemoteExampleState(
      remoteId = snippet.id,
      description = desc,
      url = url,
      filename = filename,
      uuid = uuid,
      checksum = checksum,
    )
  }

  def gitlabRemoteExamplesStatesFetch(adapterConfig: PublishAdapterConfig, after: Option[String] = None): RIO[Logging with SttpClient, List[RemoteExampleState]] = {
    val uriEither = Uri.parse(adapterConfig.graphqlEndPoint).swap.map(msg => new Error(msg)).swap
    for {
      apiURI <- RIO.fromEither(uriEither)
      query = snippetQuery(after).toRequest(apiURI, useVariables = true)
      responseOptionOption <- send(gitlabInjectAuthToken(query, adapterConfig.token)).map(_.body).absolve
      responseOption <- RIO.getOrFail(responseOptionOption) // TODO refactor
      response <- RIO.getOrFail(responseOption) // TODO refactor
      nextResults <-
        if (response.pagination.hasNextPage) gitlabRemoteExamplesStatesFetch(adapterConfig, response.pagination.endCursor)
        else RIO.succeed(List.empty[RemoteExampleState])
    } yield gitlabRemoteGistsToRemoteExampleState(response.snippets) ::: nextResults
  }
}
