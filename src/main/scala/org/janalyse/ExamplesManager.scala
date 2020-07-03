package org.janalyse

import better.files.File
import org.janalyse.externalities.github.GithubPublishAdapter
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
        .glob(globPattern,includePath = false)
        .map(file => CodeExample(file, searchRoot))
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
    val publishAdapter: PublishAdapter = new GithubPublishAdapter
    parameters
      .githubToken
      .map(publishAdapter.synchronize(examples, _))
      .getOrElse(Nil)
  }


  /**
   * Update or add an example
   * @param parameters code examples managers parameters
   * @param example
   */
  def upsert(example: CodeExample)(implicit parameters: Parameters): Change = {
    // first implementation, hard coded for github gists
    val publishAdapter: PublishAdapter = new GithubPublishAdapter
    parameters
      .githubToken
      .map(publishAdapter.exampleUpsert(example, _))
      .getOrElse(ChangeIssue(example))
  }
}

