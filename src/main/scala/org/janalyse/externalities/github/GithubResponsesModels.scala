package org.janalyse.externalities.github

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
  val uuid:Option[String] = Some(description) collect { case metaDataRE(id, _) => id }
  val sha1sum:Option[String] = Some(description) collect { case metaDataRE(_, sum) => sum }
}
object GistInfo {
  val metaDataRE="""#\s*([-0-9a-f]+)\s*/\s*([0-9a-f]+)\s*$""".r.unanchored
}


case class GistFile(
  filename: String,
  `type`: String,
  language: String,
  raw_url: String,
  size: Int,
  truncated: Boolean,
  content: String,
)

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