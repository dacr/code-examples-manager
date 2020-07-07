package fr.janalyse.cem

import better.files.File
import Hashes.sha1

trait CodeExample {
  val filename: String
  val category: Option[String] // used sub-directory
  val summary: Option[String]
  val keywords: List[String]
  val publish: List[String]
  val authors: List[String]
  val uuid: Option[String]

  def content: String

  def checksum: String

  def fileExt: String = filename.split("[.]", 2).drop(1).headOption.getOrElse("")
}

case class FileCodeExample(
  file: File,
  category: Option[String], // used sub-directory
  summary: Option[String],
  keywords: List[String],
  publish: List[String],
  authors: List[String],
  uuid: Option[String],
) extends CodeExample {
  val filename: String = file.name

  def content: String = file.contentAsString

  lazy val checksum: String = sha1(content)
}

object CodeExample {
  def extractValue(from: String)(key: String): Option[String] = {
    val RE = ("""(?m)(?i)^(?:(?://)|(?:##)|(?:- )(?:--))\s+""" + key + """\s+:\s+(.*)$""").r
    RE.findFirstIn(from).collect { case RE(value) => value.trim }
  }

  def extractValueList(from: String)(key: String): List[String] = {
    extractValue(from)(key).map(_.split("""[ \t\r,;]+""").toList).getOrElse(Nil)
  }

  def apply(file: File, searchRoot: File): FileCodeExample = {
    val category =
      file
        .path
        .getParent
        .toString
        .replace(searchRoot.toString, "")
        .replaceAll("""^[/\\]""", "")
        .replaceAll("""[/\\]$""", "")
        .trim match {
        case "" => None
        case thing => Some(thing)
      }
    val content = file.contentAsString
    val id = extractValue(content)("id")
    val idRE = "[-0-9a-f]+".r
    if (id.isDefined) assert(idRE.matches(id.get), s"INVALID UUID: $id for $file")
    FileCodeExample(
      file = file,
      category = category,
      summary = extractValue(content)("summary"),
      keywords = extractValueList(content)("keywords"),
      publish = extractValueList(content)("publish"),
      authors = extractValueList(content)("authors"),
      uuid = id,
    )
  }
}

