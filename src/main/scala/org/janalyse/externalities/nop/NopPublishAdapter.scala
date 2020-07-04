package org.janalyse.externalities.nop

import org.janalyse.{Change, CodeExample, NoChange, PublishAdapterConfig}
import org.janalyse.externalities.{AuthToken, PublishAdapter}

class NopPublishAdapter(val config: PublishAdapterConfig) extends PublishAdapter {
  override def synchronize(examples: List[CodeExample]): List[Change] = Nil
  override def exampleUpsert(example: CodeExample): Change = NoChange(example)
}
