package fr.janalyse.cem

import fr.janalyse.cem.model.RemoteExampleState
import sttp.client3.asynchttpclient.zio.SttpClient
import sttp.model.Uri
import zio.RIO
import zio.logging.Logging
import sttp.client3._
import sttp.client3.asynchttpclient.zio._
import sttp.client3.asynchttpclient.zio.stubbing._
import sttp.client3.json4s._


object RemoteOperations {

  private def gitlabRequestBase(token:String) = {
    basicRequest
      .header("PRIVATE-TOKEN", s"$token")
  }

  private def githubRequestBase(token:String) = {
    basicRequest
      .header("Authorization", s"token $token")
      .header("Accept", "application/vnd.github.v3+json")
  }

  def remoteExampleStatesFetch(adapterConfig: PublishAdapterConfig): RIO[Logging with SttpClient, Iterable[RemoteExampleState]] = {

    RIO(Iterable.empty)
  }
}
