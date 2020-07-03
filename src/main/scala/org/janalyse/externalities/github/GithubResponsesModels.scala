package org.janalyse.externalities.github

import org.janalyse.CodeExample.extractValue

import scala.util.matching.Regex

case class GistUser(
  login: String, // user name in APIs
  name: String,
  id: Int,
  public_gists: Int,
  private_gists: Int,
  followers: Int,
  following: Int,
)

case class GistFileInfo(
  filename: String,
  `type`: String,
  language: String,
  raw_url: String,
  size: Int,
)

case class GistInfo(
  id: String,
  description: String,
  html_url: String,
  public: Boolean,
  files: Map[String, GistFileInfo],
) {
  import GistInfo.metaDataRE
  val uuidOption:Option[String] = Some(description) collect { case metaDataRE(id, _) => id }
  val checksumOption:Option[String] = Some(description) collect { case metaDataRE(_, sum) => sum }
}
object GistInfo {
  // gist info meta data is stored in the description as follow :
  //   "this is a gist #8c641170-ae1d-4f5c-8010-08f2169d4ce4/8c641170-ae1d-4f5c-8010-08f2169d4ce4"
  //   for : "gist description #uuid/sha1"
  val metaDataRE:Regex = """#\s*([-0-9a-f]+)\s*/\s*([0-9a-f]+)\s*$""".r.unanchored
  def makeDescription(summary:String, uuid:String, contentSHA1:String):String = {
    s"$summary #$uuid/$contentSHA1"
  }
}


case class GistFile(
  filename: String,
  `type`: String,
  language: String,
  raw_url: String,
  size: Int,
  truncated: Boolean,
  content: String,
) {
  def uuidOption: Option[String] = extractValue(content)("id")
}

case class Gist(
  id: String,
  description: String,
  html_url: String,
  public: Boolean,
  files: Map[String, GistFile],
)

case class GistFileSpec(
  filename: String,
  content: String
)

case class GistSpec(
  description: String,
  public: Boolean,
  files: Map[String, GistFileSpec],
)