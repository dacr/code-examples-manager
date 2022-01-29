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
package fr.janalyse.cem

import zio.*
import zio.test.*
import zio.test.Assertion.*
import org.junit.runner.RunWith
import fr.janalyse.cem.model.CodeExample

//@RunWith(classOf[zio.test.junit.ZTestJUnitRunner])
object SynchronizeSpec extends DefaultRunnableSpec {
  // ----------------------------------------------------------------------------------------------
  val t1 = test("check examples coherency success with valid examples") {
    val examplesWithIssues = List(
      CodeExample(filename = "pi-1.sc", content = "42", uuid = Some("e7f1879c-c893-4b3d-bac1-f11f641e90bd")),
      CodeExample(filename = "pi-2.sc", content = "42", uuid = Some("a49b0c53-3ec3-4404-bd7d-c249a4868a2b"))
    )
    assertM(Synchronize.examplesCheckCoherency(examplesWithIssues).run)(succeeds(anything))
  }
  // ----------------------------------------------------------------------------------------------
  val t2 = test("check examples coherency should fail on duplicates UUID") {
    val examplesWithIssues = List(
      CodeExample(filename = "pi-1.sc", content = "42", uuid = Some("e7f1879c-c893-4b3d-bac1-f11f641e90bd")),
      CodeExample(filename = "pi-2.sc", content = "42", uuid = Some("e7f1879c-c893-4b3d-bac1-f11f641e90bd"))
    )
    assertM(Synchronize.examplesCheckCoherency(examplesWithIssues).run)(fails(hasMessage(containsString("duplicated UUIDs"))))
  }

  // ----------------------------------------------------------------------------------------------
  override def spec = {
    suite("CodeExample tests")(t1, t2)
  }
}
