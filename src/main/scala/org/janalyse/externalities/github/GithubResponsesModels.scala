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
  val uuid:String = GistInfo.MetaDataRE
}
object GistInfo {
  val MetaDataRE=""".*#([-0-9a-f]+)/(\d+)$""".r
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