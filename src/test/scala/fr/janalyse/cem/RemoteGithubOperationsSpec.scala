package fr.janalyse.cem

import fr.janalyse.cem.tools.DescriptionTools._
import fr.janalyse.cem.model.{CodeExample, RemoteExampleState, UpdateRemoteExample}
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import sttp.client3.asynchttpclient.zio.stubbing.whenRequestMatches
import zio.{Task, ZIO}
import zio.test.Assertion._
import zio.test.{assert, assertM}
import zio.test.junit.JUnitRunnableSpec
import zio.logging._

class RemoteGithubOperationsSpec extends JUnitRunnableSpec {

  import RemoteGithubOperations._

  val exampleTask1 = {
    val filename = "test-data/sample1/fake-testing-pi.sc"
    val searchRoot = "test-data/sample1"
    val content =
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
    CodeExample.makeExample(filename, searchRoot, Task(content))
  }

  // ----------------------------------------------------------------------------------------------
  val t1 = testM("") {
    val config = PublishAdapterConfig(
      enabled = true, kind = "github", activationKeyword = "gist", apiEndPoint = "https://api.github.com",
      overviewUUID = "cafacafe-cafecafe", token = Some("aaa-aa"), defaultVisibility = None, filenameRenameRules = Map.empty
    )

    val logic = for {
      example1 <- exampleTask1
      uuid1 <- Task.getOrFail(example1.uuid)
      state1 = RemoteExampleState(
        remoteId="6e40f8239fa6828ab45a064b8131fdfc", // // MDQ6R2lzdDQ1NTk5OTQ= --> 04:Gist4559994 --> 4559994
        description="desc",
        url="https://truc/aa-bb",
        filename=Some(example1.filename),
        uuid=uuid1,
        checksum=example1.checksum
      )
      todos = List(UpdateRemoteExample(uuid1,example1, state1))
      results <- githubRemoteExamplesChangesApply(config, todos)
    } yield results

    val stub = for {
      _ <-
        whenRequestMatches(_.uri.toString() == "https://api.github.com/gists/6e40f8239fa6828ab45a064b8131fdfc")
          .thenRespond("""{"id":"aa-bb", "html_url":"https://truc/aa-bb"}""")
    } yield ()

    val httpClientLayer = AsyncHttpClientZioBackend.stubLayer
    val stubbedLogic = stub *> logic
    val layers = Logging.ignore ++ httpClientLayer
    assertM(stubbedLogic.provideLayer(layers).run)( succeeds(anything))
  }

  // ----------------------------------------------------------------------------------------------
  override def spec = {
    suite("RemoteGithubOperationsTools tests")(t1)
  }
}
