package fr.janalyse.cem.externalities.publishadapter.gitlab

import java.time.OffsetDateTime

import org.scalatest._, OptionValues._
import org.scalatest.flatspec._
import org.scalatest.matchers._

class GitlabResponsesModelsTest extends AnyFlatSpec with should.Matchers {

  "SnippetInfo" should "encode UUID and checksum" in {
    List(
      "truc muche #26-4c-d5/42ab",
      "truc muche #26-4c-d5/42ab ",
      "truc muche #26-4c-d5 / 42ab ",
      "truc muche # 26-4c-d5 / 42ab ",
      "#26-4c-d5/42ab ",
    ).foreach { description =>
      val sample =
        SnippetInfo(
          id = 1,title="truc", fileName="file", visibility="private", author=SnippetAuthor(1,"","","","",""),
          updatedAt = OffsetDateTime.now(), createdAt=OffsetDateTime.now(), webUrl="",rawUrl="",
          description = description
        )
      sample.uuidOption.value shouldBe "26-4c-d5"
      sample.checksumOption.value shouldBe "42ab"
    }
  }

  it should "provide a description generator encoding both uuid and checksum" in {
    val description = Snippet.makeDescription("truc", "aabb", "42")
    description should fullyMatch regex "truc / .+ #aabb/42"
  }

}
