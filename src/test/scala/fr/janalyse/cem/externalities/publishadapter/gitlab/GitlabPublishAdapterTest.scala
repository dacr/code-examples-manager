//package fr.janalyse.cem.externalities.publishadapter.gitlab
//
//import org.scalatest._
//import matchers._
//import OptionValues._
//import better.files.Dsl._
//import flatspec._
//import fr.janalyse.cem.externalities.publishadapter.github.GithubPublishAdapter
//import fr.janalyse.cem.{CodeExample, Configuration}
//
//class GitlabPublishAdapterTest extends AnyFlatSpec with should.Matchers {
//
//  // Those tests are only executed with a github token is available
//
//  Configuration().publishAdapters.get("gitlab-com-snippets").foreach { config =>
//    GitlabPublishAdapter.lookup(config).collect { case adapter: GitlabPublishAdapter =>
//
//      "GitlabPublishAdapter" should "be able to list available gists" in {
//        val snippets = adapter.listSnippets()
//        info(s"found ${snippets.size} gist for connected user")
//        snippets.size shouldBe >(0)
//      }
//
//      it should "be able to publish a code example" in {
//        val example = CodeExample(pwd / "test-data" / "sample1" / "fake-testing-pi.sc", pwd / "test-data" / "sample1")
//        val result = adapter.synchronize(example :: Nil)
//        result should have size 1
//      }
//    }
//  }
//}
//
