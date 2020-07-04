package org.janalyse.externalities.github

import org.janalyse.{CodeExample, CodeExampleManagerConfig, Configuration, NoChange}
import org.scalatest._
import matchers._
import OptionValues._
import flatspec._
import better.files._
import better.files.Dsl._

class GithubPublishAdapterTest extends AnyFlatSpec with should.Matchers {

  // Those tests are only executed with a github token is available

  Configuration().publishAdapters.get("github-com-gists").foreach { config =>
    val adapter = new GithubPublishAdapter(config)

    "GithubPublishAdapter" should "be able to get authenticated user information" in {
      adapter.getUser() shouldBe defined
    }

    it should "be possible to list available gists" in {
      val user = adapter.getUser().value
      val gists = adapter.userGists(user)
      info(s"found ${gists.size} gist for user ${user.login}")
      gists.size shouldBe > (0)
    }

    it should "be possible to publish a code example" in {
      val example = CodeExample(pwd / "test-data" / "sample1" / "fake-testing-pi.sc", pwd / "test-data" / "sample1")
      adapter.synchronize(example::Nil)
    }
  }
}
