package org.janalyse

import better.files._
import better.files.Dsl._
import org.scalatest._,matchers._, OptionValues._, flatspec._


class ExamplesManagerTest extends AnyFlatSpec with should.Matchers {

  implicit val parameters:Parameters = Parameters(
    searchRoots = List(pwd / "test-data" / "sample1" , pwd / "test-data" / "sample2"),
    filesGlob = Some("**/*.{sc,sh}"),
    githubToken = None,
    gitlabToken = None,
    examplesOverviewUUID = "fade-fade"
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
