package org.janalyse.externalities

import org.janalyse.CodeExample

trait PublishAdapter {
  def synchronize(examples:List[CodeExample], authToken: AuthToken):Int
}
