// summary : Smallest doodle example.
// keywords : vector-graphics, doodle
// publish : gist, snippet
// authors : @crodav
// id : 1e8cd65f-8531-4d34-ad4e-993f0ccf2341

import $ivy.`org.creativescala::doodle:0.9.15`

import doodle.image._
import doodle.core._
import doodle.image.syntax._
import doodle.java2d._

Image.circle(10).fillColor(Color.red).draw()

scala.io.StdIn.readLine("Enter to exit...") // required when run as a script
