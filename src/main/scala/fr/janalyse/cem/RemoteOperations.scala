package fr.janalyse.cem

import fr.janalyse.cem.model.RemoteExampleState
import sttp.client3.asynchttpclient.zio.SttpClient
import sttp.model.Uri
import zio.RIO
import zio.logging._
import sttp.client3._
import sttp.client3.asynchttpclient.zio._
import sttp.client3.asynchttpclient.zio.stubbing._
import sttp.client3.json4s._

object RemoteOperations {

  private def gitlabRequestBase(tokenOption: Option[String]) = {
    val base = basicRequest
    tokenOption.fold(base)(token => base.header("PRIVATE-TOKEN", s"$token"))
  }

  private def injectAuthToken[A,B](request:Request[A,B], tokenOption: Option[String]) = {
    val base = request.header("Accept", "application/vnd.github.v3+json")
    tokenOption.fold(base)(token => base.header("Authorization", s"token $token"))
  }

  case class RemoteGist(id: String, description: Option[String])

  case class RemoteGistConnection(/*gists: Option[List[RemoteGist]],*/ totalCount: Int)

  val gistQuery = {
    import fr.janalyse.cem.graphql.github.GitHubClient.Gist
    import fr.janalyse.cem.graphql.github.GitHubClient.GistConnection
    import fr.janalyse.cem.graphql.github.GitHubClient.User.gists
    import fr.janalyse.cem.graphql.github.GitHubClient.Query.viewer
    viewer(
      gists()(
        (
            //GistConnection.nodes((Gist.id ~ Gist.description).mapN(RemoteGist)) ~
            GistConnection.totalCount
          )
          .map(RemoteGistConnection)
      )
    )
  }

  def remoteExampleStatesFetch(adapterConfig: PublishAdapterConfig): RIO[Logging with SttpClient, Iterable[RemoteExampleState]] = {
    for {
      _ <- log.info("Using " + adapterConfig.kind)
      if adapterConfig.kind == "github"
      graphqlAPI = uri"https://api.github.com/graphql"
      query = gistQuery.toRequest(graphqlAPI, useVariables = true)
      response <- send(injectAuthToken(query, adapterConfig.token)).map(_.body).absolve
      _ <- log.info("Gists count "+response.totalCount)
    } yield Nil
  }
}
