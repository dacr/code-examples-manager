package org.janalyse

import better.files._
import better.files.Dsl._
import org.janalyse.externalities.AuthToken


object ExamplesManager {
  def examples(implicit parameters: Parameters): List[CodeExample] = {
    val found = for {
      searchRoot <- parameters.searchRoots
      globPattern <- parameters.filesGlob
    } yield {
      searchRoot
        .glob(globPattern)
        .map(CodeExample(_))
        .toList
        .filter(_.id.isDefined)
    }
    found.flatten
  }

  def updateGitHubGistRemoteExamples(implicit parameters: Parameters): Unit = {
    implicit val token = AuthToken(scala.util.Properties.envOrElse("GIST_TOKEN", "invalid-token"))
    val user = "dacr"
  }
}

