/*
 * Copyright 2023 David Crosson
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

import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.logging.*
import fr.janalyse.cem.model.*
import fr.janalyse.cem.model.WhatToDo.*
import fr.janalyse.cem.tools.DescriptionTools.*
import org.junit.runner.RunWith
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import sttp.capabilities.zio.ZioStreams
import sttp.capabilities.WebSockets
import sttp.client3.testing.SttpBackendStub
import zio.nio.file.Path
import zio.lmdb.LMDB

@RunWith(classOf[zio.test.junit.ZTestJUnitRunner])
class RemoteGithubOperationsSpec extends ZIOSpecDefault {

  import RemoteGithubOperations.*

  val zexample1 = {
    val filename   = Path("test-data/sample1/fake-testing-pi.sc")
    val searchRoot = Path("test-data/sample1")
    val content    =
      """// summary : Simplest scalatest test framework usage.
        |// keywords : scalatest, pi, @testable
        |// publish : gist, snippet
        |// authors : David Crosson
        |// license : GPL
        |// id : 8f2e14ba-9856-4500-80ab-3b9ba2234ce2
        |// execution : scala ammonite script (http://ammonite.io/) - run as follow 'amm scriptname.sc'
        |
        |import $ivy.`org.scalatest::scalatest:3.2.0`
        |import org.scalatest._,matchers.should.Matchers._
        |
        |math.Pi shouldBe 3.14d +- 0.01d""".stripMargin
    CodeExample
      .buildFromFile(filename, searchRoot)
      .provide(
        FileSystemServiceStub.stubWithContents(Map(filename -> content)),
        Scope.default,
        LMDB.live // TODO - Replace with TestLMDB when available
      )
  }

  // ----------------------------------------------------------------------------------------------
  val t1 = test("apply changes") {
    val config = PublishAdapterConfig(
      enabled = true,
      kind = "github",
      activationKeyword = "gist",
      apiEndPoint = "https://api.github.com",
      overviewUUID = "cafacafe-cafecafe",
      token = Some("aaa-aa"),
      defaultVisibility = None,
      filenameRenameRules = Map.empty
    )

    val logic = for {
      example1 <- zexample1
      uuid1     = example1.uuid
      state1    = RemoteExampleState(
                    remoteId = "6e40f8239fa6828ab45a064b8131fdfc", // // MDQ6R2lzdDQ1NTk5OTQ= --> 04:Gist4559994 --> 4559994
                    description = "desc",
                    url = "https://truc/aa-bb",
                    files = List(example1.filename),
                    uuid = uuid1,
                    hash = example1.hash
                  )
      todos     = List(UpdateRemoteExample(uuid1, example1, state1))
      results  <- githubRemoteExamplesChangesApply(config, todos)
    } yield results

    val stub: SttpBackendStub[Task, Any] = AsyncHttpClientZioBackend.stub
      .whenRequestMatches(_.uri.toString() == "https://api.github.com/gists/6e40f8239fa6828ab45a064b8131fdfc")
      .thenRespond("""{"id":"aa-bb", "html_url":"https://truc/aa-bb"}""")

    val stubLayer = ZLayer.succeed(stub)

    logic.provide(stubLayer).map(result => assertTrue(true))
  }

  // ----------------------------------------------------------------------------------------------
  override def spec = {
    suite("RemoteGithubOperationsTools tests")(t1)
  }
}
