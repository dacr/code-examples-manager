package fr.janalyse.cem

import fr.janalyse.cem.model.RemoteExampleState
import sttp.client3.asynchttpclient.zio.SttpClient
import zio.RIO
import zio.logging._
import sttp.client3._
import sttp.client3.asynchttpclient.zio._
import zio.stream.ZStream


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

  def gistQuery(after:Option[String]=None, first:Option[Int]=Some(100) ) = {
    import fr.janalyse.cem.graphql.github.Client._
    import fr.janalyse.cem.graphql.github.Client.PageInfo._
    import fr.janalyse.cem.graphql.github.Client.User.gists
    import fr.janalyse.cem.graphql.github.Client.Query.viewer
    import fr.janalyse.cem.graphql.github.Client.GistConnection._

    viewer(
      gists(first=first, after= after, orderBy=Some(GistOrder(direction=OrderDirection.DESC, field=GistOrderField.CREATED_AT)))(
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

  def githubRemoteExamplesStatesFetch(adapterConfig: PublishAdapterConfig, after:Option[String]=None):RIO[Logging with SttpClient, List[RemoteExampleState]] = {
    val query = gistQuery(after).toRequest(uri"https://api.github.com/graphql", useVariables = true)
    for {
      response <- send(injectAuthToken(query, adapterConfig.token)).map(_.body).absolve
      nextResults <-
        if (response.pagination.hasNextPage) githubRemoteExamplesStatesFetch(adapterConfig, response.pagination.endCursor)
        else RIO.succeed(List.empty[RemoteExampleState])
      gists = for {
        gist <- response.gists
        desc <- gist.description
        (uuid,checksum) <- extractMetaDataFromDescription(desc)
      }  yield RemoteExampleState(remoteId = gist.id, description = desc, uuid=uuid, checksum = checksum)
    } yield gists:::nextResults
  }

  def remoteExampleStatesFetch(adapterConfig: PublishAdapterConfig): RIO[Logging with SttpClient, Iterable[RemoteExampleState]] = {
    for {
      _ <- log.info(s"Checking ${adapterConfig.kind}/${adapterConfig.activationKeyword}")
      states <-
        if (adapterConfig.kind == "github") githubRemoteExamplesStatesFetch(adapterConfig)
        else if (adapterConfig.kind == "gitlab") RIO.succeed(List.empty[RemoteExampleState])
        else RIO.succeed(List.empty[RemoteExampleState])
    } yield states
  }
}
