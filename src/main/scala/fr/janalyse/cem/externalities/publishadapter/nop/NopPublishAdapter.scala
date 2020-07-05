package fr.janalyse.cem.externalities.publishadapter.nop

import fr.janalyse.cem.{Change, CodeExample, NoChange, PublishAdapterConfig}
import fr.janalyse.cem.CodeExample
import fr.janalyse.cem.externalities.publishadapter.{AuthToken, PublishAdapter}

class NopPublishAdapter(val config: PublishAdapterConfig) extends PublishAdapter {
  override def synchronize(examples: List[CodeExample]): List[Change] = Nil
  override def exampleUpsert(example: CodeExample): Change = NoChange(example)
}
