package org.janalyse.externalities.gitlab

import org.janalyse.{Change, CodeExample, CodeExampleManagerConfig, PublishAdapterConfig}
import org.janalyse.externalities.{AuthToken, PublishAdapter}

object GitlabPublishAdapter {
  def lookup(config:PublishAdapterConfig):Option[PublishAdapter] = {
    None
  }
}

class GitlabPublishAdapter(val config: PublishAdapterConfig) extends PublishAdapter {
  override def synchronize(examples: List[CodeExample]): List[Change] = ???

  override def exampleUpsert(example: CodeExample): Change = ???
}
