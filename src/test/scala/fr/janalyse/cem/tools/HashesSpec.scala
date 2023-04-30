/*
 * Copyright 2023 David Crosson
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

import zio.*
import zio.test.*
import zio.test.TestAspect.*
import zio.test.Gen.*
import zio.test.Assertion.*
import org.junit.runner.RunWith
import fr.janalyse.cem.tools.Hashes.*

@RunWith(classOf[zio.test.junit.ZTestJUnitRunner])
class HashesSpec extends ZIOSpecDefault {

  def spec = {
    suite("Hash function tests")(
      // ----------------------------------------------------------------------------------------------
      test("sha1 compute the right hash value") {
        val example = "Please hash me !"
        assertTrue(sha1(example) == "4031d74d6a72919da236a388bdf3b966126b80f2")
      },
      // ----------------------------------------------------------------------------------------------
      test("sha1 should not fail") {
        assertTrue(sha1("") == "da39a3ee5e6b4b0d3255bfef95601890afd80709")
      },
      // ----------------------------------------------------------------------------------------------
      test("sha1 hashes are never empty") {
        check(Gen.string) { content =>
          assert(sha1(content))(isNonEmptyString)
        }
      },
      test("sha1 hashes are different if their content are differents") {
        check(Gen.string, Gen.string) { (content1, content2)  =>
          assertTrue(content1 != content2 && sha1(content1) != sha1(content2)) ||
          assertTrue(content1 == content2 && sha1(content1) == sha1(content2))
        }
      }
    )
  }
}
