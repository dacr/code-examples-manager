package org.janalyse.externalities.github

import org.janalyse.{CodeExample, Parameters}
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.OptionValues._
import better.files._
import better.files.Dsl._

class GithubPublishAdapterTest extends FlatSpec with Matchers {

  // Those tests are only executed with a github token is available

  Parameters.githubToken.foreach { token =>
    implicit val authTokenMadeImplicit = token
    val adapter = new GitHubPublishAdapter

    "GithubPublishAdapter" should "be able to get authenticated user information" ignore {
      adapter.getUser shouldBe defined
    }

    it should "be possible to list available gists" ignore {
      val user = adapter.getUser.value
      val gists = adapter.userGists(user)
      info(s"found ${gists.size} gist for user ${user.login}")
      gists.size shouldBe > (0)
    }

    it should "be possible to publish a code example" ignore {
      val example = CodeExample(pwd / "test-data" / "sample1" / "fake-testing-pi.sc" )
      adapter.synchronize(example::Nil, token)
    }
  }
}
