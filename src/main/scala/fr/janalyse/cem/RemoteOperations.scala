package fr.janalyse.cem

import caliban.client.Operations.RootQuery
import caliban.client.SelectionBuilder
import fr.janalyse.cem.model.RemoteExampleState
import sttp.client3.asynchttpclient.zio.SttpClient
import zio.RIO
import zio.logging._
import sttp.client3._
import sttp.client3.asynchttpclient.zio._
import sttp.model.Uri


object RemoteOperationsTools {
  private val metaDataRE = """#\s*([-0-9a-f]+)\s*/\s*([0-9a-f]+)\s*$""".r.unanchored

  def extractMetaDataFromDescription(description: String): Option[(String, String)] = {
    metaDataRE
      .findFirstMatchIn(description)
      .filter(_.groupCount == 2)
      .map(m => (m.group(1), m.group(2)))
  }

}

// =====================================================================================================================

object GitlabRemoteOperations {
  private def gitlabInjectAuthToken[A,B](request:Request[A,B], tokenOption: Option[String]) = {
    val base = request.header("Content-Type","application/json")
    tokenOption.fold(base)(token => base.header("Authorization", s"Bearer $token"))
  }

  case class RemoteSnippet(id: String, description: Option[String])

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
          nodes((Snippet.id ~ Snippet.description).mapN(RemoteSnippet)) ~
            pageInfo((endCursor ~ hasNextPage).mapN(RemoteSnippetPagination))
          ).map { case (Some(a), c) => RemoteSnippetConnection(a.flatten, c) }
      )
    )
  }

  def githubRemoteGistsToRemoteExampleState(snippets: List[RemoteSnippet]): List[RemoteExampleState] = {
    for {
      snippet <- snippets
      desc <- snippet.description
      (uuid, checksum) <- RemoteOperationsTools.extractMetaDataFromDescription(desc)
    } yield RemoteExampleState(remoteId = snippet.id, description = desc, uuid = uuid, checksum = checksum)
  }

  def gitlabRemoteExamplesStatesFetch(adapterConfig: PublishAdapterConfig, after: Option[String] = None): RIO[Logging with SttpClient, List[RemoteExampleState]] = {
    val uriEither = Uri.parse(adapterConfig.apiEndPoint).swap.map(msg => new Error(msg)).swap
    for {
      apiURI <- RIO.fromEither(uriEither)
      query = snippetQuery(after).toRequest(apiURI, useVariables = true)
      responseOptionOption <- send(gitlabInjectAuthToken(query, adapterConfig.token)).map(_.body).absolve
      responseOption <- RIO.getOrFail(responseOptionOption) // TODO refactor
      response <- RIO.getOrFail(responseOption) // TODO refactor
      nextResults <-
        if (response.pagination.hasNextPage) gitlabRemoteExamplesStatesFetch(adapterConfig, response.pagination.endCursor)
        else RIO.succeed(List.empty[RemoteExampleState])
    } yield githubRemoteGistsToRemoteExampleState(response.snippets) ::: nextResults
  }

}

// =====================================================================================================================

object GithubRemoteOperations {
  private def githubInjectAuthToken[A, B](request: Request[A, B], tokenOption: Option[String]) = {
    val base = request.header("Accept", "application/vnd.github.v3+json")
    tokenOption.fold(base)(token => base.header("Authorization", s"token $token"))
  }

  case class RemoteGist(id: String, description: Option[String])

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
          nodes((Gist.id ~ Gist.description).mapN(RemoteGist)) ~
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
      (uuid, checksum) <- RemoteOperationsTools.extractMetaDataFromDescription(desc)
    } yield RemoteExampleState(remoteId = gist.id, description = desc, uuid = uuid, checksum = checksum)
  }

  def githubRemoteExamplesStatesFetch(adapterConfig: PublishAdapterConfig, after: Option[String] = None): RIO[Logging with SttpClient, List[RemoteExampleState]] = {
    val uriEither = Uri.parse(adapterConfig.apiEndPoint).swap.map(msg => new Error(msg)).swap
    for {
      apiURI <- RIO.fromEither(uriEither)
      query = gistQuery(after).toRequest(apiURI, useVariables = true)
      response <- send(githubInjectAuthToken(query, adapterConfig.token)).map(_.body).absolve
      nextResults <-
        if (response.pagination.hasNextPage) githubRemoteExamplesStatesFetch(adapterConfig, response.pagination.endCursor)
        else RIO.succeed(List.empty[RemoteExampleState])
    } yield githubRemoteGistsToRemoteExampleState(response.gists) ::: nextResults
  }
}





object RemoteOperations {

  def remoteExampleStatesFetch(adapterConfig: PublishAdapterConfig): RIO[Logging with SttpClient, Iterable[RemoteExampleState]] = {
    for {
      _ <- log.info(s"Checking ${adapterConfig.kind}/${adapterConfig.activationKeyword}")
      states <-
        if (adapterConfig.kind == "github") GithubRemoteOperations.githubRemoteExamplesStatesFetch(adapterConfig)
        else if (adapterConfig.kind == "gitlab") GitlabRemoteOperations.gitlabRemoteExamplesStatesFetch(adapterConfig)
        else RIO.succeed(List.empty[RemoteExampleState])
    } yield states
  }
}
