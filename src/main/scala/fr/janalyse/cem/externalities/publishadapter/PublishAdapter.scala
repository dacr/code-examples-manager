//package fr.janalyse.cem.externalities.publishadapter
//
//import fr.janalyse.cem.{Change, CodeExample, PublishAdapterConfig}
//import fr.janalyse.cem.model.CodeExample
//
//trait PublishAdapter {
//  val config:PublishAdapterConfig
//  def synchronize(examples:List[CodeExample]):List[Change]
//  def exampleUpsert(example:CodeExample):Change
//
//  def fileRename(filename:String, config: PublishAdapterConfig):String = {
//    config.filenameRenameRules.values.foldLeft(filename){ (current, rule) => rule.rename(current)}
//  }
//}
