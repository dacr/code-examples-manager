package org.janalyse

import better.files.File
import org.janalyse.externalities.github.GithubPublishAdapter
import org.janalyse.externalities.gitlab.GitlabPublishAdapter
import org.janalyse.externalities.{AuthToken, PublishAdapter}


object ExamplesManager {
  /**
   * Search for local code examples.
   *
   * @param config
   * @return found code examples
   */
  def getExamples(implicit config: CodeExampleManagerConfig): List[CodeExample] = {
    val found = for {
      searchRoot <- config.examples.searchRoots
      globPattern <- config.examples.filesGlob
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
   * @param examples list of examples to synchronize
   * @param adapter publish adapter to use
   * @return number of examples updated
   */
  def synchronize(examples: List[CodeExample], adapter:PublishAdapter): Seq[Change] = {
    adapter.synchronize(examples)
  }


  /**
   * Update or add an example
   * @param example
   * @param adapter publish adapter to use
   * @return update state
   */
  def upsert(example: CodeExample, adapter:PublishAdapter): Change = {
    adapter.exampleUpsert(example)
  }
}

