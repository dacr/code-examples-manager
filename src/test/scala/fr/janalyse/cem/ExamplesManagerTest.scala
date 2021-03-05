package fr.janalyse.cem

import better.files._
import better.files.Dsl._
import org.scalatest._
import matchers._
import OptionValues._
import flatspec._
import fr.janalyse.cem.externalities.publishadapter.AuthToken


class ExamplesManagerTest extends AnyFlatSpec with should.Matchers {

  implicit val config: CodeExampleManagerConfig =
    CodeExampleManagerConfig(
      ExamplesConfig(
        searchRootDirectories = List(pwd / "test-data" / "sample1", pwd / "test-data" / "sample2").map(_.path).mkString(","),
        searchGlob = Some("**/*.{sc,sh}",
        )
      ),
      Map(
        "gitlab-com" -> PublishAdapterConfig(
          enabled = false,
          kind = "github",
          activationKeyword = "gist",
          apiEndPoint = "https://api.github.com",
          overviewUUID = "fade-fade",
          token = None,
          defaultVisibility = None,
          filenameRenameRules = Map.empty,
        )
      ),
      MetaConfig(None,None,None,None,None,None,None)
    )

  "ExamplesManager" should "be able to list locally available examples" in {
    val examplesByFileExt =
      ExamplesManager
        .getExamples
        .groupBy(_.fileExt)
        .view
        .mapValues(_.size)
    examplesByFileExt.get("sc").value should be(2)
    examplesByFileExt.get("sh").value should be(1)
  }

  it should "be able to get a gist example using its IDs" in {
  }

  it should "be able to synchronize remotes" in {
  }

}
