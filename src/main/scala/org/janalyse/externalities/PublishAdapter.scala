package org.janalyse.externalities

import org.janalyse.{Change, CodeExample}

trait PublishAdapter {
  def synchronize(examples:List[CodeExample], authToken: AuthToken):List[Change]
  def exampleUpsert(example:CodeExample, authToken: AuthToken):Change
}
