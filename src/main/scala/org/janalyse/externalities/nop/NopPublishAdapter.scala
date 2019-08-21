package org.janalyse.externalities.nop

import org.janalyse.CodeExample
import org.janalyse.externalities.{AuthToken, PublishAdapter}

class NopPublishAdapter extends PublishAdapter {
  override def synchronize(examples: List[CodeExample], authToken: AuthToken): Int = {
    0
  }
}
