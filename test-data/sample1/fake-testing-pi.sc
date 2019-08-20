// summary : Simplest scalatest test framework usage.
// keywords : scalatest, pi
// publish : gist, snippet
// authors : @crodav
// id : d24d8cb3-45c0-4d88-b033-7fae2325607b

import $ivy.`org.scalatest::scalatest:3.0.6`
import org.scalatest._,Matchers._

math.Pi shouldBe 3.14d +- 0.01d
