package org.janalyse

import scala.util.Properties._

case class Parameters(
  searchRootDirectories: Option[String],
  filesGlob: Option[String]
)

object Parameters {
  def propOrEnvOrNone(key: String): Option[String] = envOrSome(key, propOrNone(key))

  def apply(): Parameters = {
    Parameters(
      searchRootDirectories = propOrEnvOrNone("CODE_EXAMPLES_SEARCH_ROOTS"),
      filesGlob = propOrEnvOrNone("CODE_EXAMPLES_SEARCH_GLOB"),
    )
  }
}