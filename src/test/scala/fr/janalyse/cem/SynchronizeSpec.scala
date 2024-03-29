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
package fr.janalyse.cem

import zio.*
import zio.test.*
import zio.test.Assertion.*
import org.junit.runner.RunWith
import fr.janalyse.cem.model.{CodeExample, ExampleIssue}

import java.util.UUID
import scala.util.Success

@RunWith(classOf[zio.test.junit.ZTestJUnitRunner])
class SynchronizeSpec extends ZIOSpecDefault {
  // ----------------------------------------------------------------------------------------------
  val t1 = test("check examples coherency success with valid examples") {
    val examplesWithIssues: List[Either[ExampleIssue, CodeExample]] = List(
      Right(CodeExample.build(filepath = None, filename = "pi-1.sc", content = "42", uuid = UUID.fromString("e7f1879c-c893-4b3d-bac1-f11f641e90bd"))),
      Right(CodeExample.build(filepath = None, filename = "pi-2.sc", content = "42", uuid = UUID.fromString("a49b0c53-3ec3-4404-bd7d-c249a4868a2b")))
    )
    assertZIO(Synchronize.examplesCheckCoherency(examplesWithIssues))(isUnit)
  }
  // ----------------------------------------------------------------------------------------------
  val t2 = test("check examples coherency should fail on duplicates UUID") {
    val examplesWithIssues: List[Either[ExampleIssue, CodeExample]] = List(
      Right(CodeExample.build(filepath = None, filename = "pi-1.sc", content = "42", uuid = UUID.fromString("e7f1879c-c893-4b3d-bac1-f11f641e90bd"))),
      Right(CodeExample.build(filepath = None, filename = "pi-2.sc", content = "42", uuid = UUID.fromString("e7f1879c-c893-4b3d-bac1-f11f641e90bd")))
    )
    //assertZIO(Synchronize.examplesCheckCoherency(examplesWithIssues).exit)(fails(isSubtype[Exception](anything)))
    assertZIO(Synchronize.examplesCheckCoherency(examplesWithIssues).exit)(fails(hasMessage(containsString("Duplicated UUIDs"))))
  }

  // ----------------------------------------------------------------------------------------------
  override def spec = {
    suite("CodeExample tests")(t1, t2)
  }
}
