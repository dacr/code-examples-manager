package fr.janalyse.cem

import zio.{Exit, ZIO}
import zio.test._
import zio.test.Assertion._
import zio.test.junit.JUnitRunnableSpec

class SynchronizeSpec extends JUnitRunnableSpec {
  // ----------------------------------------------------------------------------------------------
  val t1 = testM("check examples coherency success with valid examples") {
    val examplesWithIssues = List(
      CodeExample(filename="pi-1.sc", content="42", uuid=Some("e7f1879c-c893-4b3d-bac1-f11f641e90bd")),
      CodeExample(filename="pi-2.sc", content="42", uuid=Some("a49b0c53-3ec3-4404-bd7d-c249a4868a2b")),
    )
    assertM(Synchronize.checkExamplesCoherency(examplesWithIssues).run)(succeeds(anything))
  }
  // ----------------------------------------------------------------------------------------------
  val t2 = testM("check examples coherency should fail on duplicates UUID") {
    val examplesWithIssues = List(
      CodeExample(filename="pi-1.sc", content="42", uuid=Some("e7f1879c-c893-4b3d-bac1-f11f641e90bd")),
      CodeExample(filename="pi-2.sc", content="42", uuid=Some("e7f1879c-c893-4b3d-bac1-f11f641e90bd")),
    )
    assertM(Synchronize.checkExamplesCoherency(examplesWithIssues).run)(fails(hasMessage(containsString("duplicated UUIDs"))))
  }

  // ----------------------------------------------------------------------------------------------
  override def spec = {
    suite("CodeExample tests")(t1,t2)
  }
}