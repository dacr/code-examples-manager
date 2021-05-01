package fr.janalyse.cem.tools

import fr.janalyse.cem.model.CodeExample
import fr.janalyse.cem.tools.DescriptionTools._
import zio.test.Assertion._
import zio.test.assert
import zio.test.junit.JUnitRunnableSpec


class DescriptionToolsSpec extends JUnitRunnableSpec {


  // ----------------------------------------------------------------------------------------------
  val t1 = test("extractMetaDataFromDescription can return example uuid and checksum from the description") {
    val description = "Blah example / published by https://github.com/dacr/code-examples-manager #7135b214-5b48-47d0-afd7-c7f64c0a31c3/5ec6b73c57561e0cc578dea654eeddce09433252"
    assert(extractMetaDataFromDescription(description))(
      equalTo(Some("7135b214-5b48-47d0-afd7-c7f64c0a31c3"->"5ec6b73c57561e0cc578dea654eeddce09433252")
      ))
  }

  // ----------------------------------------------------------------------------------------------
  val t2 = test("extractMetaDataFromDescription should return none if no uuid checksum is encoded in the description") {
    assert(extractMetaDataFromDescription(""))(equalTo(None))
    assert(extractMetaDataFromDescription("blah bouh"))(equalTo(None))
  }

  // ----------------------------------------------------------------------------------------------
  val t3 = test("makeDescription should return a description for ready to publish code examples") {
    assert(makeDescription(CodeExample(filename="truc.sc", uuid = Some("aaaa"), content="blah"))) (
      isSome(
        endsWithString("#aaaa/")
      )
    )
  }

  // ----------------------------------------------------------------------------------------------
  override def spec = {
    suite("RemoteOperationsTools tests")(t1, t2)
  }
}
