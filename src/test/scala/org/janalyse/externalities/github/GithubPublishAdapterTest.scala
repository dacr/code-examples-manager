package org.janalyse.externalities.github

import org.janalyse.{CodeExample, Parameters}
import org.scalatest._
import matchers._
import OptionValues._
import flatspec._
import better.files._
import better.files.Dsl._
import org.janalyse.externalities.AuthToken

class GithubPublishAdapterTest extends AnyFlatSpec with should.Matchers {

  // Those tests are only executed with a github token is available

  Parameters.githubToken.foreach { token =>
    implicit val authTokenMadeImplicit:AuthToken = token
    val adapter = new GithubPublishAdapter

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
      adapter.synchronize(example::Nil, token)
    }
  }
}
