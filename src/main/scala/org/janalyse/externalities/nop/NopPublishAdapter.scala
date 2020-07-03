package org.janalyse.externalities.nop

import org.janalyse.{Change, CodeExample, NoChange}
import org.janalyse.externalities.{AuthToken, PublishAdapter}

class NopPublishAdapter extends PublishAdapter {
  override def synchronize(examples: List[CodeExample], authToken: AuthToken): List[Change] = Nil
  override def exampleUpsert(example: CodeExample, authToken: AuthToken): Change = NoChange(example, Map.empty)
}
