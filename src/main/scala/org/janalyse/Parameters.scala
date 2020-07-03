package org.janalyse

import scala.util.Properties._
import better.files._
import org.janalyse.externalities.AuthToken

case class Parameters(
  searchRoots: List[File],
  filesGlob: Option[String],
  gitlabToken: Option[AuthToken],
  githubToken: Option[AuthToken],
  examplesOverviewUUID: String,
)

object Parameters {
  def propOrEnvOrNone(key: String): Option[String] = envOrSome(key, propOrNone(key))

  val searchRootDirectories: Option[String] = propOrEnvOrNone("CEM_SEARCH_ROOTS")
  val filesGlob: Option[String] = propOrEnvOrNone("CEM_SEARCH_GLOB")
  val gitlabToken: Option[AuthToken] = propOrEnvOrNone("CEM_GITLAB_TOKEN").map(AuthToken)
  val githubToken: Option[AuthToken] = propOrEnvOrNone("CEM_GITHUB_TOKEN").map(AuthToken)
  val examplesOverviewUUID: String =
    propOrEnvOrNone("CEM_EXAMPLES_OVERVIEW_UUID")
      .getOrElse("cafacafe-cafecafe")

  def apply(): Parameters = {
    val searchRoots =
      searchRootDirectories
        .map(_.split("""\s*,""").toList)
        .getOrElse(Nil)
        .map(dir => dir.toFile)
        .filter(_.exists)
        .filter(_.isDirectory)
    Parameters(
      searchRoots = searchRoots,
      filesGlob = filesGlob,
      gitlabToken = gitlabToken,
      githubToken = githubToken,
      examplesOverviewUUID = examplesOverviewUUID,
    )
  }
}