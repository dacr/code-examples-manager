package org.janalyse

import org.scalatest.{FlatSpec, Matchers}
import better.files._

class CodeExampleTest extends FlatSpec with Matchers {
  "CodeExample" can "be constructed from a local file" in {
    val ex1 = CodeExample(file"test-data/sample1/fake-testing-pi.sc")
    ex1.fileExt shouldBe "sc"
    ex1.filename shouldBe "fake-testing-pi.sc"
    ex1.content should include regex "id [:] d24d8cb3-45c0-4d88-b033-7fae2325607b"
  }

}
