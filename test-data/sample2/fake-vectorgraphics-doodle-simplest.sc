// summary : Smallest doodle example.
// keywords : vector-graphics, doodle
// publish :
// authors : @crodav
// id : 1e8cd65f-8531-4d34-ad4e-993f0ccf2341


interp.configureCompiler(_.settings.YpartialUnification.value=true)
@
import $ivy.`org.creativescala::doodle:0.9.4`

import doodle.syntax._
import doodle.java2d._

Picture{ implicit alg => alg.circle(100) }.draw()

scala.io.StdIn.readLine("Enter to exit...") // required when run as a script
