package org.janalyse

import better.files.File
import Hashes.sha1

case class CodeExample(
  file: File,
  summary: Option[String],
  keywords: List[String],
  publish: List[String],
  authors: List[String],
  uuid: Option[String],
) {
  def content: String = file.contentAsString

  lazy val checksum: String = sha1(content)

  def filename: String = file.name

  def fileExt: String = filename.split("[.]", 2).drop(1).headOption.getOrElse("")
}

object CodeExample {
  def extractValue(from: String)(key: String): Option[String] = {
    val RE = ("""(?m)(?i)^(?:(?://)|(?:##))\s+""" + key + """\s+:\s+(.*)$""").r
    RE.findFirstIn(from).collect { case RE(value) => value.trim }
  }

  def extractValueList(from: String)(key: String): List[String] = {
    extractValue(from)(key).map(_.split("""[ \t\r,;]+""").toList).getOrElse(Nil)
  }

  def apply(file: File): CodeExample = {
    val content = file.contentAsString
    CodeExample(
      file = file,
      summary = extractValue(content)("summary"),
      keywords = extractValueList(content)("keywords"),
      publish = extractValueList(content)("publish"),
      authors = extractValueList(content)("authors"),
      uuid = extractValue(content)("id"),
    )
  }
}

