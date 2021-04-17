//package fr.janalyse.cem.externalities.publishadapter.gitlab
//
//import java.time.OffsetDateTime
//
//import fr.janalyse.cem.model.CodeExample.extractValue
//
//import scala.util.matching.Regex
//
//case class SnippetAuthor(
//  id: Long,
//  name: String,
//  username: String,
//  state: String,
//  avatarUrl: String,
//  webUrl: String
//)
//
//case class SnippetInfo(
//  id: Long,
//  title: String,
//  fileName: String,
//  description: String,
//  visibility: String,
//  author: SnippetAuthor,
//  updatedAt: OffsetDateTime,
//  createdAt: OffsetDateTime,
//  webUrl: String,
//  rawUrl: String,
//) {
//
//  import SnippetInfo.metaDataRE
//
//  val uuidOption: Option[String] = Some(description) collect { case metaDataRE(id, _) => id }
//  val checksumOption: Option[String] = Some(description) collect { case metaDataRE(_, sum) => sum }
//}
//
//object SnippetInfo {
//  // snippet info meta data is stored in the description as follow :
//  //   "this is a snippet #8c641170-ae1d-4f5c-8010-08f2169d4ce4/8c641170-ae1d-4f5c-8010-08f2169d4ce4"
//  //   for : "snippet description #uuid/sha1"
//  val metaDataRE: Regex = """#\s*([-0-9a-f]+)\s*/\s*([0-9a-f]+)\s*$""".r.unanchored
//}
//
//case class Snippet(
//  title: String,
//  fileName: String,
//  content: String,
//  description: String,
//  visibility: String,
//) {
//  def uuidOption: Option[String] = extractValue(content)("id")
//}
//
//object Snippet {
//  def makeDescription(summary: String, uuid: String, contentSHA1: String): String = {
//    s"$summary / published by https://github.com/dacr/code-examples-manager #$uuid/$contentSHA1"
//  }
//}
//
//case class SnippetUpdate(
//  id: Long,
//  title: String,
//  fileName: String,
//  content: String,
//  description: String,
//  visibility: String,
//)