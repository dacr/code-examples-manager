package fr.janalyse.cem

import fr.janalyse.cem.model._
import sttp.client3.asynchttpclient.zio.SttpClient
import zio.RIO
import zio.logging._



object RemoteOperations {

  def remoteExampleStatesFetch(adapterConfig: PublishAdapterConfig): RIO[Logging with SttpClient, Iterable[RemoteExampleState]] = {
    val targetName = s"${adapterConfig.kind}/${adapterConfig.activationKeyword}"
    for {
      _ <- log.info(s"$targetName : Fetching already published examples")
      states <-
        if (adapterConfig.kind == "github") RemoteGithubOperations.githubRemoteExamplesStatesFetch(adapterConfig)
        else if (adapterConfig.kind == "gitlab") RemoteGitlabOperations.gitlabRemoteExamplesStatesFetch(adapterConfig)
        else RIO.fail(new Exception(s"$targetName : Unsupported adapter kind ${adapterConfig.kind}"))
    } yield states
  }

  def remoteExamplesChangesApply(adapterConfig: PublishAdapterConfig, todos: Iterable[WhatToDo]):RIO[Logging with SttpClient, Iterable[RemoteExample]] = {
    val targetName = s"${adapterConfig.kind}/${adapterConfig.activationKeyword}"
    for {
      _ <- log.info(s"$targetName : Applying changes")
      _ <- log.info(s"$targetName : To add count ${todos.count(_.isInstanceOf[AddExample])}")
      _ <- log.info(s"$targetName : To update count ${todos.count(_.isInstanceOf[UpdateRemoteExample])}")
      _ <- log.info(s"$targetName : To keep count ${todos.count(_.isInstanceOf[KeepRemoteExample])}")
      remoteExamples <-
        if (adapterConfig.kind == "github") RemoteGithubOperations.githubRemoteExamplesChangesApply(adapterConfig, todos)
        //else if (adapterConfig.kind == "gitlab") GitlabRemoteOperations.gitlabRemoteExamplesChangesApply(adapterConfig, todos)
        else RIO.fail(new Exception(s"$targetName : Unsupported adapter kind ${adapterConfig.kind}"))
    } yield remoteExamples
  }

}
