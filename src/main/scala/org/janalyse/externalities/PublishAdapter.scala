package org.janalyse.externalities

import org.janalyse.{Change, CodeExample, PublishAdapterConfig}

trait PublishAdapter {
  val config:PublishAdapterConfig
  def synchronize(examples:List[CodeExample]):List[Change]
  def exampleUpsert(example:CodeExample):Change
}
