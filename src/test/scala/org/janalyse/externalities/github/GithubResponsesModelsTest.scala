package org.janalyse.externalities.github

import org.scalatest._
import org.scalatest.OptionValues._

class GithubResponsesModelsTest extends FlatSpec with Matchers {

  "GistInfo" should "encode UUID and checksum" in {
    List(
      "truc muche #26-4c-d5/42ab",
      "truc muche #26-4c-d5/42ab ",
      "truc muche #26-4c-d5 / 42ab ",
      "truc muche # 26-4c-d5 / 42ab ",
      "#26-4c-d5/42ab ",
    ).foreach { description =>
      val sample =
        GistInfo(
          id = "xx", html_url = "", public = true, files = Map.empty,
          description = description
        )
      sample.uuid.value shouldBe "26-4c-d5"
      sample.sha1sum.value shouldBe "42ab"
    }
  }

  it should "provide a description generator encoding both uuid and checksum" in {
    val description = GistInfo.makeDescription("truc", "aabb", "42")
    description shouldBe "truc #aabb/42"
  }

}
