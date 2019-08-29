package org.janalyse

import org.janalyse.externalities.github.GitHubPublishAdapter
import org.janalyse.externalities.{AuthToken, PublishAdapter}


object ExamplesManager {
  /**
   * Search for local code examples.
   *
   * @param parameters
   * @return found code examples
   */
  def getExamples(implicit parameters: Parameters): List[CodeExample] = {
    val found = for {
      searchRoot <- parameters.searchRoots
      globPattern <- parameters.filesGlob
    } yield {
      searchRoot
        .glob(globPattern)
        .map(CodeExample(_))
        .toList
        .filter(_.uuid.isDefined)
    }
    found.flatten
  }

  /**
   * Synchronize local examples with remote publication sites (github, gitlab))
   *
   * @param examples   list of examples to synchronize
   * @param parameters code examples managers parameters
   * @return number of examples updated
   */
  def synchronize(examples: List[CodeExample])(implicit parameters: Parameters): List[Change] = {
    // first implementation, hard coded for github gists
    val publishAdapter: PublishAdapter = new GitHubPublishAdapter
    parameters
      .githubToken
      .map(publishAdapter.synchronize(examples, _))
      .getOrElse(Nil)
  }

  /**
   * Migrate remote gists mainly (only to be used when an older publish mechanism has been used)
   * @param parameters code examples managers parameters
   * @param examples
   */
  def migrate(examples: List[CodeExample])(implicit parameters: Parameters): List[Change] = {
    // first implementation, hard coded for github gists
    val publishAdapter: PublishAdapter = new GitHubPublishAdapter
    parameters
      .githubToken
      .map(publishAdapter.migrateGists(examples, _))
      .getOrElse(Nil)
  }
}

