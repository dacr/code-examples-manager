// summary : Simplest scalatest test framework usage.
// keywords : scalatest, pi
// publish : gist, snippet
// authors : @crodav
// id : 8f2e14ba-9856-4500-80ab-3b9ba2234ce2

import $ivy.`org.scalatest::scalatest:3.0.6`
import org.scalatest._,Matchers._

math.Pi shouldBe 3.14d +- 0.01d
