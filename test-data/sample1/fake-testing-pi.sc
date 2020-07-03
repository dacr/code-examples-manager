// summary : Simplest scalatest test framework usage.
// keywords : scalatest, pi, @testable
// publish : gist, snippet
// authors : David Crosson
// license : GPL
// id : 8f2e14ba-9856-4500-80ab-3b9ba2234ce2
// execution : scala ammonite script (http://ammonite.io/) - run as follow 'amm scriptname.sc'

import $ivy.`org.scalatest::scalatest:3.2.0`
import org.scalatest._,matchers.should.Matchers._

math.Pi shouldBe 3.14d +- 0.01d
