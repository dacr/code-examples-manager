package fr.janalyse.cem.tools

import sttp.model.Uri
import zio.{RIO, Task}

object HttpTools {
  def uriParse(link: String): Task[Uri] = {
    RIO.fromEither(Uri.parse(link).swap.map(msg => new Error(msg)).swap)
  }

  def webLinkingExtractNext(link:String):Option[String] = {
    // Using Web Linking to get large amount of results : https://tools.ietf.org/html/rfc5988
    val nextLinkRE = """.*<([^>]+)>; rel="next".*""".r
    nextLinkRE.findFirstMatchIn(link).map(_.group(1))
  }

}
