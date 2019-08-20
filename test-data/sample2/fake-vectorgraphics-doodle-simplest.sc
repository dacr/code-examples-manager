// summary : Smallest doodle example.
// keywords : vector-graphics, doodle
// publish :
// authors : @crodav
// id : 5423ee0c-032e-47f7-b8a5-af5a1ec75338


interp.configureCompiler(_.settings.YpartialUnification.value=true)
@
import $ivy.`org.creativescala::doodle:0.9.4`

import doodle.syntax._
import doodle.java2d._

Picture{ implicit alg => alg.circle(100) }.draw()

scala.io.StdIn.readLine("Enter to exit...") // required when run as a script
