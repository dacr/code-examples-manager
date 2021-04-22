package fr.janalyse.cem.model

/*
enum WhatToDo {
  case IgnoreExample(example:CodeExample)
  case DeleteRemoteExample(uuid:String, state:RemoteExampleState)
  case AddExample(uuid:String, example:CodeExample)
  case KeepRemoteExample(uuid:String, example:CodeExample, state:RemoteExampleState)
  case UpdateRemoteExample(uuid:String, example:CodeExample, state:RemoteExampleState)
  case UnsupportedOperation(uuidOption:Option[String], exampleOption:Option[CodeExample], stateOption:Option[RemoteExampleState])
}
*/

trait WhatToDo
case class IgnoreExample(example:CodeExample) extends WhatToDo
case class DeleteRemoteExample(uuid:String, state:RemoteExampleState) extends WhatToDo
case class AddExample(uuid:String, example:CodeExample) extends WhatToDo
case class KeepRemoteExample(uuid:String, example:CodeExample, state:RemoteExampleState) extends WhatToDo
case class UpdateRemoteExample(uuid:String, example:CodeExample, state:RemoteExampleState) extends WhatToDo
case class UnsupportedOperation(uuidOption:Option[String], exampleOption:Option[CodeExample], stateOption:Option[RemoteExampleState]) extends WhatToDo



case class RemoteExample(example:CodeExample, state:RemoteExampleState)

//
//import java.net.URL
//
//sealed trait Change {
//  val example:CodeExample
//  val publishedUrl:Option[String]
//  val kind:String
//  override def toString: String =
//    s"""$kind ${example.uuid.getOrElse("?")} - ${example.summary.getOrElse("?")} - ${publishedUrl.getOrElse("?")}"""
//}
//
//case class NoChange(example:CodeExample,publishedUrl:Option[String]=None) extends Change {
//  override val kind = "UNCHANGED"
//}
//case class UpdatedChange(example:CodeExample,publishedUrl:Option[String]) extends Change {
//  override val kind = "UPDATED"
//}
//case class AddedChange(example:CodeExample,publishedUrl:Option[String]) extends Change {
//  override val kind = "ADDED"
//}
//case class DeletedChange(example:CodeExample) extends Change {
//  override val kind = "DELETED"
//  override val publishedUrl: Option[String] = None
//}
//case class ChangeIssue(example:CodeExample) extends Change {
//  override val kind = "NOT DONE!"
//  override val publishedUrl: Option[String] = None
//}
