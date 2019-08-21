package org.janalyse

import org.scalatest.{FlatSpec, Matchers}
import better.files._

class CodeExampleTest extends FlatSpec with Matchers {
  "CodeExample" can "be constructed from a local file" in {
    val ex1 = CodeExample(file"test-data/sample1/fake-testing-pi.sc")
    ex1.fileExt shouldBe "sc"
    ex1.filename shouldBe "fake-testing-pi.sc"
    ex1.content should include regex "id [:] 8f2e14ba-9856-4500-80ab-3b9ba2234ce2"
  }

}
