package org.janalyse

import scala.util.Properties._
import better.files._
import org.janalyse.externalities.AuthToken

case class Parameters(
  searchRoots: List[File],
  filesGlob: Option[String],
  gitlabToken: Option[AuthToken],
  githubToken: Option[AuthToken],
) {
}

object Parameters {
  def propOrEnvOrNone(key: String): Option[String] = envOrSome(key, propOrNone(key))

  val searchRootDirectories = propOrEnvOrNone("CEM_SEARCH_ROOTS")
  val filesGlob = propOrEnvOrNone("CEM_SEARCH_GLOB")
  val gitlabToken = propOrEnvOrNone("CEM_GITLAB_TOKEN").map(AuthToken)
  val githubToken = propOrEnvOrNone("CEM_GITHUB_TOKEN").map(AuthToken)

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
    )
  }
}