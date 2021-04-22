package fr.janalyse.cem.tools

import fr.janalyse.cem.tools.Hashes._
import zio.test.Assertion._
import zio.test.assert
import zio.test.junit.JUnitRunnableSpec


class HashesSpec extends JUnitRunnableSpec {


  // ----------------------------------------------------------------------------------------------
  val t1 = test("sha1 compute the right hash value") {
    val example = "Please hash me !"
    assert(sha1(example))(equalTo("4031d74d6a72919da236a388bdf3b966126b80f2"))
  }

  // ----------------------------------------------------------------------------------------------
  val t2 = test("sha1 should not fail") {
    assert(sha1(""))(equalTo("da39a3ee5e6b4b0d3255bfef95601890afd80709"))
    assert(sha1(null))(equalTo("da39a3ee5e6b4b0d3255bfef95601890afd80709"))
  }

  // ----------------------------------------------------------------------------------------------
  override def spec = {
    suite("Hash function tests")(t1, t2)
  }
}
