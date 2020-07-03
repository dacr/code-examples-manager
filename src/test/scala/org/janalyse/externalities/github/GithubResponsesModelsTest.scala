package org.janalyse.externalities.github

import org.scalatest._,matchers._, OptionValues._, flatspec._

class GithubResponsesModelsTest extends AnyFlatSpec with should.Matchers {

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
      sample.uuidOption.value shouldBe "26-4c-d5"
      sample.checksumOption.value shouldBe "42ab"
    }
  }

  it should "provide a description generator encoding both uuid and checksum" in {
    val description = GistInfo.makeDescription("truc", "aabb", "42")
    description shouldBe "truc #aabb/42"
  }

}
