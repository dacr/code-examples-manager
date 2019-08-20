package org.janalyse

import better.files._
import better.files.Dsl._
import org.scalatest.{FlatSpec, Matchers}

class ExamplesManagerTest extends FlatSpec with Matchers {

  val cwd = pwd.path.toString

  implicit val parameters = Parameters(
    searchRootDirectories = Some(s"$cwd/test-data/sample1,$cwd/test-data/sample2"),
    filesGlob = Some("*.{sc,sh}")
  )

  "ExamplesManager" should "be able to list locally available examples" in {
    val examplesByFileExt =
      ExamplesManager
        .examples
        .groupBy(_.fileExt)
        .mapValues(_.size)
    examplesByFileExt.get("sc").get should be(2)
    examplesByFileExt.get("sh").get should be(1)
  }

  it should "be able to get a gist example using its IDs" in {
  }

  it should "be able to synchronize remotes" in {
  }

}
