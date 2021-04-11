package fr.janalyse.cem

import Hashes.sha1
import zio.Task

import java.io.File

case class CodeExample(
  filename: String,
  category: Option[String]=None, // used sub-directory
  summary: Option[String]=None,
  keywords: List[String]=Nil,
  publish: List[String]=Nil,
  authors: List[String]=Nil,
  uuid: Option[String]=None,
  content: String
) {
  def fileExt: String = filename.split("[.]", 2).drop(1).headOption.getOrElse("")

  val checksum: String = {
    // only filename and category is defined outside from example content
    sha1(content + filename + category.getOrElse(""))
  }
}


object CodeExample {
  def exampleContentExtractValue(from: String, key: String): Option[String] = {
    val RE = ("""(?m)(?i)^(?:(?:// )|(?:## )|(?:- )(?:-- )) *""" + key + """ *: *(.*)$""").r
    RE.findFirstIn(from).collect { case RE(value) => value.trim }.filter(_.size>0)
  }

  def exampleContentExtractValueList(from: String, key: String): List[String] = {
    exampleContentExtractValue(from, key).map(_.split("""\s*[,;]\s*""").toList).getOrElse(Nil)
  }

  def filenameFromFilepath(filepath:String):String = {
    new File(filepath).getName
  }

  def exampleCategoryFromFilepath(filename: String, searchRoot: String): Option[String] = {
    val normalizedSearchRoot = new File(searchRoot).getPath
    val file = new File(filename)
    val parentFilename = if (file.getParent!=null) file.getParent else ""
    parentFilename
      .replace(normalizedSearchRoot, "")
      .replaceAll("""^[/\\]""", "")
      .replaceAll("""[/\\]$""", "")
      .trim match {
      case "" => None
      case category => Some(category)
    }
  }

  def makeExample(exampleFilepath: String, fromSearchRoot: String, contentFetcher: Task[String]): Task[CodeExample] = {
    for {
      rawContent <- contentFetcher
      filename = filenameFromFilepath(exampleFilepath)
      category = exampleCategoryFromFilepath(exampleFilepath, fromSearchRoot)
      content = rawContent.replaceAll("\r", "")
    } yield {
      val id = exampleContentExtractValue(content, "id")
      val idRE = "[-0-9a-f]+".r
      if (id.isDefined) assert(idRE.matches(id.get), s"INVALID UUID: $id for $filename")
      CodeExample(
        filename = filename,
        category = category,
        summary = exampleContentExtractValue(content, "summary"),
        keywords = exampleContentExtractValueList(content, "keywords"),
        publish = exampleContentExtractValueList(content, "publish"),
        authors = exampleContentExtractValueList(content, "authors"),
        uuid = id,
        content = content
      )
    }
  }

}

