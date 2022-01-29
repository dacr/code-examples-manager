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

import zio.test.*
import zio.test.Assertion.*
import fr.janalyse.cem.model.CodeExample
import fr.janalyse.cem.tools.DescriptionTools.*
import org.junit.runner.RunWith

//@RunWith(classOf[zio.test.junit.ZTestJUnitRunner])
object DescriptionToolsSpec extends DefaultRunnableSpec {

  override def spec = {
    suite("RemoteOperationsTools tests")(
      // ----------------------------------------------------------------------------------------------
      test("extractMetaDataFromDescription can return example uuid and checksum from the description") {
        val description = "Blah example / published by https://github.com/dacr/code-examples-manager #7135b214-5b48-47d0-afd7-c7f64c0a31c3/5ec6b73c57561e0cc578dea654eeddce09433252"
        assertTrue(extractMetaDataFromDescription(description).contains("7135b214-5b48-47d0-afd7-c7f64c0a31c3" -> "5ec6b73c57561e0cc578dea654eeddce09433252"))
      },
      // ----------------------------------------------------------------------------------------------
      test("extractMetaDataFromDescription should return none if no uuid checksum is encoded in the description") {
        assertTrue(extractMetaDataFromDescription("").isEmpty) &&
        assertTrue(extractMetaDataFromDescription("blah bouh").isEmpty)
      },
      // ----------------------------------------------------------------------------------------------
      test("makeDescription should return a description for ready to publish code examples") {
        val example = CodeExample(filename = "truc.sc", uuid = Some("aaaa"), content = "blah")
        assert(makeDescription(example))(
          isSome(
            endsWithString(s"#aaaa/${example.checksum}")
          )
        )
      }
    )
  }
}
