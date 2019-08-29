package org.janalyse.externalities.nop

import org.janalyse.{Change, CodeExample}
import org.janalyse.externalities.{AuthToken, PublishAdapter}

class NopPublishAdapter extends PublishAdapter {
  override def synchronize(examples: List[CodeExample], authToken: AuthToken): List[Change] = Nil
  override def migrateGists(examples: List[CodeExample], authToken: AuthToken): List[Change] = Nil
}
