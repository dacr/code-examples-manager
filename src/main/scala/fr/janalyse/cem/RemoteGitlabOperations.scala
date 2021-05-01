package fr.janalyse.cem

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

  def gitlabRemoteExamplesStatesFetch(adapterConfig: PublishAdapterConfig, after: Option[String] = None): RIO[Logging with SttpClient, Iterable[RemoteExampleState]] = {
    ???
  }

  def gitlabRemoteExampleAdd(adapterConfig:PublishAdapterConfig, addExample:AddExample):RIO[Logging with SttpClient, RemoteExample] = {
    ???
  }

  def gitlabRemoteExampleUpdate(adapterConfig:PublishAdapterConfig, update:UpdateRemoteExample):RIO[Logging with SttpClient, RemoteExample] = {
    ???
  }

  def gitlabRemoteExampleChangesApply(adapterConfig: PublishAdapterConfig)(todo: WhatToDo) : RIO[Logging with SttpClient, Option[RemoteExample]] = {
    todo match {
      case _:IgnoreExample => ZIO.succeed(None)
      case _:UnsupportedOperation => ZIO.succeed(None)
      case _:OrphanRemoteExample => ZIO.succeed(None)
      case KeepRemoteExample(uuid, example, state) => ZIO.succeed(Some(RemoteExample(example, state)))
      case exampleTODO:UpdateRemoteExample => gitlabRemoteExampleUpdate(adapterConfig, exampleTODO).asSome
      case exampleTODO:AddExample => gitlabRemoteExampleAdd(adapterConfig, exampleTODO).asSome
    }
  }

  def gitlabRemoteExamplesChangesApply(adapterConfig: PublishAdapterConfig, todos: Iterable[WhatToDo]): RIO[Logging with SttpClient, Iterable[RemoteExample]] = {
    for {
      remotes <- RIO.foreach(todos)(gitlabRemoteExampleChangesApply(adapterConfig)).map(_.flatten)
    } yield remotes
  }

}
