package fr.janalyse.cem

import fr.janalyse.cem.model._
import sttp.client3.asynchttpclient.zio.SttpClient
import zio.RIO
import zio.logging._



object RemoteOperations {

  def remoteExampleStatesFetch(adapterConfig: PublishAdapterConfig): RIO[Logging with SttpClient, Iterable[RemoteExampleState]] = {
    for {
      _ <- log.info(s"Checking ${adapterConfig.kind}/${adapterConfig.activationKeyword}")
      states <-
        if (adapterConfig.kind == "github") RemoteGithubOperations.githubRemoteExamplesStatesFetch(adapterConfig)
        else if (adapterConfig.kind == "gitlab") RemoteGitlabOperations.gitlabRemoteExamplesStatesFetch(adapterConfig)
        else RIO.fail(new Exception(s"Unsupported adapter kind ${adapterConfig.kind}"))
    } yield states
  }

  def remoteExamplesChangesApply(adapterConfig: PublishAdapterConfig, todos: Iterable[WhatToDo]):RIO[Logging with SttpClient, Iterable[RemoteExample]] = {
    val target = s"${adapterConfig.kind}/${adapterConfig.activationKeyword}"
    for {
      _ <- log.info(s"Applying changes to $target")
      _ <- log.info(s"To add count ${todos.count(_.isInstanceOf[AddExample])} for $target")
      _ <- log.info(s"To update count ${todos.count(_.isInstanceOf[UpdateRemoteExample])} for $target")
      _ <- log.info(s"To keep count ${todos.count(_.isInstanceOf[KeepRemoteExample])} for $target")
      remoteExamples <-
        if (adapterConfig.kind == "github") RemoteGithubOperations.githubRemoteExamplesChangesApply(adapterConfig, todos)
        //else if (adapterConfig.kind == "gitlab") GitlabRemoteOperations.gitlabRemoteExamplesChangesApply(adapterConfig, todos)
        else RIO.fail(new Exception(s"Unsupported adapter kind ${adapterConfig.kind}"))
    } yield remoteExamples
  }

}
