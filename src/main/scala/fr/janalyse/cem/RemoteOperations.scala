/*
 * Copyright 2021 David Crosson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.janalyse.cem

import fr.janalyse.cem.model.*
import fr.janalyse.cem.model.WhatToDo.*
import sttp.client3.asynchttpclient.zio.SttpClient
import zio.*

object RemoteOperations {

  def remoteExampleStatesFetch(adapterConfig: PublishAdapterConfig): RIO[SttpClient, Iterable[RemoteExampleState]] = {
    for {
      _      <- ZIO.logInfo(s"${adapterConfig.targetName} : Fetching already published examples")
      states <-
        if (adapterConfig.kind == "github") RemoteGithubOperations.githubRemoteExamplesStatesFetch(adapterConfig)
        else if (adapterConfig.kind == "gitlab") RemoteGitlabOperations.gitlabRemoteExamplesStatesFetch(adapterConfig)
        else RIO.fail(new Exception(s"${adapterConfig.targetName} : Unsupported adapter kind ${adapterConfig.kind}"))
    } yield states
  }

  def remoteExamplesChangesApply(adapterConfig: PublishAdapterConfig, todos: Iterable[WhatToDo]): RIO[SttpClient, Iterable[RemoteExample]] = {
    for {
      //_ <- ZIO.logInfo(s"${adapterConfig.targetName} : Applying changes")
      //_ <- ZIO.logInfo(s"${adapterConfig.targetName} : To add count ${todos.count(_.isInstanceOf[AddExample])}")
      //_ <- ZIO.logInfo(s"${adapterConfig.targetName} : To update count ${todos.count(_.isInstanceOf[UpdateRemoteExample])}")
      //_ <- ZIO.logInfo(s"${adapterConfig.targetName} : To keep count ${todos.count(_.isInstanceOf[KeepRemoteExample])}")
      remoteExamples <-
        if (adapterConfig.kind == "github") RemoteGithubOperations.githubRemoteExamplesChangesApply(adapterConfig, todos)
        else if (adapterConfig.kind == "gitlab") RemoteGitlabOperations.gitlabRemoteExamplesChangesApply(adapterConfig, todos)
        else RIO.fail(new Exception(s"${adapterConfig.targetName} : Unsupported adapter kind ${adapterConfig.kind}"))
    } yield remoteExamples
  }

}
