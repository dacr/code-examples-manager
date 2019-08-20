package org.janalyse

import scala.util.Properties._
import better.files._

case class Parameters(
  searchRootDirectories: Option[String],
  filesGlob: Option[String]
) {
  val searchRoots =
    searchRootDirectories
      .map(_.split("""\s*,""").toList)
      .getOrElse(Nil)
      .map(dir => dir.toFile)
      .filter(_.exists)
      .filter(_.isDirectory)
}

object Parameters {
  def propOrEnvOrNone(key: String): Option[String] = envOrSome(key, propOrNone(key))

  def apply(): Parameters = {
    Parameters(
      searchRootDirectories = propOrEnvOrNone("CODE_EXAMPLES_SEARCH_ROOTS"),
      filesGlob = propOrEnvOrNone("CODE_EXAMPLES_SEARCH_GLOB"),
    )
  }
}