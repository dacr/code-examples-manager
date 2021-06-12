package fr.janalyse.cem

import fr.janalyse.cem.model.*
import fr.janalyse.cem.model.WhatToDo.*
import sttp.client3.asynchttpclient.zio.SttpClient
import zio.RIO
import zio.logging.*

object RemoteOperations {

  def remoteExampleStatesFetch(adapterConfig: PublishAdapterConfig): RIO[Logging with SttpClient, Iterable[RemoteExampleState]] = {
    for {
      _      <- log.info(s"${adapterConfig.targetName} : Fetching already published examples")
      states <-
        if (adapterConfig.kind == "github") RemoteGithubOperations.githubRemoteExamplesStatesFetch(adapterConfig)
        else if (adapterConfig.kind == "gitlab") RemoteGitlabOperations.gitlabRemoteExamplesStatesFetch(adapterConfig)
        else RIO.fail(new Exception(s"${adapterConfig.targetName} : Unsupported adapter kind ${adapterConfig.kind}"))
    } yield states
  }

  def remoteExamplesChangesApply(adapterConfig: PublishAdapterConfig, todos: Iterable[WhatToDo]): RIO[Logging with SttpClient, Iterable[RemoteExample]] = {
    for {
      //_ <- log.info(s"${adapterConfig.targetName} : Applying changes")
      //_ <- log.info(s"${adapterConfig.targetName} : To add count ${todos.count(_.isInstanceOf[AddExample])}")
      //_ <- log.info(s"${adapterConfig.targetName} : To update count ${todos.count(_.isInstanceOf[UpdateRemoteExample])}")
      //_ <- log.info(s"${adapterConfig.targetName} : To keep count ${todos.count(_.isInstanceOf[KeepRemoteExample])}")
      remoteExamples <-
        if (adapterConfig.kind == "github") RemoteGithubOperations.githubRemoteExamplesChangesApply(adapterConfig, todos)
        else if (adapterConfig.kind == "gitlab") RemoteGitlabOperations.gitlabRemoteExamplesChangesApply(adapterConfig, todos)
        else RIO.fail(new Exception(s"${adapterConfig.targetName} : Unsupported adapter kind ${adapterConfig.kind}"))
    } yield remoteExamples
  }

}
