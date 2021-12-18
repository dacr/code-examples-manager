/*
 * Copyright 2021 David Crosson
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

import org.junit.runner.RunWith
import zio.Task
import zio.test.Assertion.*
import zio.test.*

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
  val t1 = testM("make an example") {
    for {
      example <- CodeExample.makeExample(exampleFakeTestingFilename, exampleFakeTestingSearchRoot, Task(exampleFakeTestingPiContent))
    } yield assert(example.filename)(equalTo("fake-testing-pi.sc")) &&
      assert(example.category)(equalTo(None)) &&
      assert(example.summary)(equalTo(Some("Simplest scalatest test framework usage."))) &&
      assert(example.fileExt)(equalTo("sc")) &&
      assert(example.publish)(equalTo(List("gist", "snippet"))) &&
      assert(example.authors)(equalTo(List("David Crosson"))) &&
      assert(example.keywords)(equalTo(List("scalatest", "pi", "@testable"))) &&
      assert(example.uuid)(equalTo(Some("8f2e14ba-9856-4500-80ab-3b9ba2234ce2"))) &&
      assert(example.content)(matchesRegex("(?s).*id [:] 8f2e14ba-9856-4500-80ab-3b9ba2234ce2.*")) &&
      assert(example.checksum)(equalTo("5f6dd8a2d2f813ee946542161503d61cb9a8074e"))
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
        assert(CodeExample.exampleCategoryFromFilepath(filename, searchRoot))(equalTo(expectedResult))
      }
      .reduce(_ && _)
  }

  // ----------------------------------------------------------------------------------------------
  val t3 = test("meta data single value extraction") {
    import CodeExample.{exampleContentExtractValue => extractor}
    assert(extractor("// summary : hello", "summary"))(equalTo(Option("hello"))) &&
    assert(extractor("// summary :", "summary"))(isNone) &&
    assert(extractor("// summary : ", "summary"))(isNone) &&
    assert(extractor("// truc : \n// summary : \n// machin : \n", "summary"))(isNone) &&
    assert(extractor("// truc : \n// summary :\n// machin : \n", "summary"))(isNone)
  }
  // ----------------------------------------------------------------------------------------------
  val t4 = test("meta data list value extraction") {
    import CodeExample.{exampleContentExtractValueList => extractor}
    assert(extractor("// publish : toto", "publish"))(equalTo(List("toto"))) &&
    assert(extractor("// publish :", "publish"))(isEmpty) &&
    assert(extractor("// publish : ", "publish"))(isEmpty)
  }
  // ----------------------------------------------------------------------------------------------
  override def spec = {
    suite("CodeExample tests")(t1, t2, t3, t4)
  }

}
