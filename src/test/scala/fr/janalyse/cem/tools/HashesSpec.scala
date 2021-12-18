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
package fr.janalyse.cem.tools

import fr.janalyse.cem.tools.Hashes.*
import org.junit.runner.RunWith
import zio.test.Assertion.*
import zio.test.*

//@RunWith(classOf[zio.test.junit.ZTestJUnitRunner])
object HashesSpec extends DefaultRunnableSpec {

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
