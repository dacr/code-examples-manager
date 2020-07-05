package fr.janalyse.cem.externalities.publishadapter

import fr.janalyse.cem.{Change, CodeExample, PublishAdapterConfig}
import fr.janalyse.cem.CodeExample

trait PublishAdapter {
  val config:PublishAdapterConfig
  def synchronize(examples:List[CodeExample]):List[Change]
  def exampleUpsert(example:CodeExample):Change
}
