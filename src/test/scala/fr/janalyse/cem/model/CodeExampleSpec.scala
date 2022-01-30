/*
 * Copyright 2022 David Crosson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.janalyse.cem.model

import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.nio.file.Path
import org.junit.runner.RunWith

//@RunWith(classOf[zio.test.junit.ZTestJUnitRunner])
object CodeExampleSpec extends DefaultRunnableSpec {

  val exampleFakeTestingFilename   = "test-data/sample1/fake-testing-pi.sc"
  val exampleFakeTestingSearchRoot = "test-data/sample1"
  val exampleFakeTestingPiContent  =
    """// summary : Simplest scalatest test framework usage.
      |// keywords : scalatest, pi, @testable
      |// publish : gist, snippet
      |// authors : David Crosson
      |// license : GPL
      |// id : 8f2e14ba-9856-4500-80ab-3b9ba2234ce2
      |// execution : scala ammonite script (http://ammonite.io/) - run as follow 'amm scriptname.sc'
      |
      |import $ivy.`org.scalatest::scalatest:3.2.0`
      |import org.scalatest._,matchers.should.Matchers._
      |
      |math.Pi shouldBe 3.14d +- 0.01d""".stripMargin

  // ----------------------------------------------------------------------------------------------
  val t1 = test("make an example") {
    for {
      example <- CodeExample.makeExample(exampleFakeTestingFilename, exampleFakeTestingSearchRoot, exampleFakeTestingPiContent)
    } yield assertTrue(example.filename == "fake-testing-pi.sc") &&
      assertTrue(example.category.isEmpty) &&
      assertTrue(example.summary.contains("Simplest scalatest test framework usage.")) &&
      assertTrue(example.fileExtension == "sc") &&
      assertTrue(example.publish == List("gist", "snippet")) &&
      assertTrue(example.authors == List("David Crosson")) &&
      assertTrue(example.keywords == List("scalatest", "pi", "@testable")) &&
      assertTrue(example.uuid.toString == "8f2e14ba-9856-4500-80ab-3b9ba2234ce2") &&
      assert(example.content)(matchesRegex("(?s).*id [:] 8f2e14ba-9856-4500-80ab-3b9ba2234ce2.*")) &&
      assertTrue(example.checksum == "5f6dd8a2d2f813ee946542161503d61cb9a8074e")
  }

  // ----------------------------------------------------------------------------------------------
  val t2 = test("automatically recognize categories from sub-directory name") {
    val inputsAndExpectedResults: List[((String, String), Option[String])] = List(
      ("test-data/fake-testing-pi.sc", "test-data/")                -> None,
      ("test-data/fake-testing-pi.sc", "")                          -> Some("test-data"),
      ("test-data/sample1/fake-testing-pi.sc", "test-data/sample1") -> None,
      ("test-data/sample1/fake-testing-pi.sc", "test-data")         -> Some("sample1"),
      ("test-data/sample1/fake-testing-pi.sc", "test-data/")        -> Some("sample1"),
      ("test-data/sample1/fake-testing-pi.sc", "")                  -> Some("test-data/sample1")
    )
    inputsAndExpectedResults
      .map { case ((filename, searchRoot), expectedResult) =>
        assertTrue(CodeExample.exampleCategoryFromFilepath(Path(filename), Path(searchRoot)) == expectedResult)
      }
      .reduce(_ && _)
  }

  // ----------------------------------------------------------------------------------------------
  val t3 = test("meta data single value extraction") {
    import CodeExample.{exampleContentExtractValue => extractor}
    assertTrue(extractor("// summary : hello", "summary") == Option("hello")) &&
    assert(extractor("// summary :", "summary"))(isNone) &&
    assert(extractor("// summary : ", "summary"))(isNone) &&
    assert(extractor("// truc : \n// summary : \n// machin : \n", "summary"))(isNone) &&
    assert(extractor("// truc : \n// summary :\n// machin : \n", "summary"))(isNone)
  }
  // ----------------------------------------------------------------------------------------------
  val t4 = test("meta data list value extraction") {
    import CodeExample.{exampleContentExtractValueList => extractor}
    assertTrue(extractor("// publish : toto", "publish") == List("toto")) &&
    assertTrue(extractor("// publish :", "publish").isEmpty) &&
    assertTrue(extractor("// publish : ", "publish").isEmpty)
  }
  // ----------------------------------------------------------------------------------------------
  override def spec = {
    suite("CodeExample tests")(t1, t2, t3, t4)
  }

}
