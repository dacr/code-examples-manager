package fr.janalyse.cem

import fr.janalyse.cem.model.RemoteExampleState
import sttp.client3.asynchttpclient.zio.SttpClient
import zio.RIO
import zio.logging._
import sttp.client3._
import sttp.client3.asynchttpclient.zio._


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
  case class RemoteGistConnection(gists: List[RemoteGist], totalCount: Int, pagination: RemoteGistPagination)
  case class RemoteGistPagination(endCursor:Option[String], hasNextPage:Boolean)

  val gistQuery = {
    import fr.janalyse.cem.graphql.github.GitHubClient._
    import fr.janalyse.cem.graphql.github.GitHubClient.PageInfo._
    import fr.janalyse.cem.graphql.github.GitHubClient.User.gists
    import fr.janalyse.cem.graphql.github.GitHubClient.Query.viewer
    import fr.janalyse.cem.graphql.github.GitHubClient.GistConnection._

    viewer(
      gists(first=Some(100), orderBy=Some(GistOrder(direction=OrderDirection.DESC, field=GistOrderField.CREATED_AT)))(
        (
            nodes((Gist.id ~ Gist.description).mapN(RemoteGist)) ~
            totalCount ~
            pageInfo((endCursor ~ hasNextPage).mapN(RemoteGistPagination))
        ).map{case ((Some(a),b),c) => RemoteGistConnection(a.flatten,b,c)}
      )
    )
  }

  private val metaDataRE = """#\s*([-0-9a-f]+)\s*/\s*([0-9a-f]+)\s*$""".r.unanchored
  def extractMetaDataFromDescription(description: String):Option[(String,String)] = {
    metaDataRE
      .findFirstMatchIn(description)
      .filter(_.groupCount == 2)
      .map(m => (m.group(1), m.group(2)))
  }

  def remoteExampleStatesFetch(adapterConfig: PublishAdapterConfig): RIO[Logging with SttpClient, Iterable[RemoteExampleState]] = {
    for {
      _ <- log.info(s"checking ${adapterConfig.kind}/${adapterConfig.activationKeyword}")
      if adapterConfig.kind == "github"
      graphqlAPI = uri"https://api.github.com/graphql"
      query = gistQuery.toRequest(graphqlAPI, useVariables = true)
      response <- send(injectAuthToken(query, adapterConfig.token)).map(_.body).absolve
      _ <- log.info("Gists count "+response.totalCount)
      // TODO manage pagination using gists(after=Some("xx")) && pagination.endCursor
    } yield for {
      gist <- response.gists
      desc <- gist.description
      (uuid,checksum) <- extractMetaDataFromDescription(desc)
    }  yield RemoteExampleState(remoteId = gist.id, description = desc, uuid=uuid, checksum = checksum)
  }
}
