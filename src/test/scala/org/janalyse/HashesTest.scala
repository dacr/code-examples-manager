package org.janalyse

import org.scalatest.{FlatSpec, Matchers}
import Hashes._

class HashesTest extends FlatSpec with Matchers {
  override def suiteName: String = "HashesTest"

  val example = "Please hash me !"

  "murmurHash3" should "return the right hash" in {
    murmurHash3(example) shouldBe 370020140
  }

  "md5sum" should "return the right hash" in {
    md5sum(example) shouldBe "55340948b4b044ad6d1632908c86e765"
  }

  "sha1" should "return the right hash" in {
    sha1(example) shouldBe "4031d74d6a72919da236a388bdf3b966126b80f2"
  }
}
