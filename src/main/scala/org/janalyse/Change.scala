package org.janalyse

import java.net.URL

sealed trait Change {
  val example:CodeExample
  val publishedUrls:Map[String, String]
  val kind:String
  override def toString: String =
    s"""$kind ${example.uuid.getOrElse("?")} - ${example.summary.getOrElse("?")}"""
}

case class NoChange(example:CodeExample,publishedUrls:Map[String,String]) extends Change {
  override val kind = "UNCHANGED"
}
case class UpdatedChange(example:CodeExample,publishedUrls:Map[String,String]) extends Change {
  override val kind = "UPDATED"
}
case class AddedChange(example:CodeExample,publishedUrls:Map[String,String]) extends Change {
  override val kind = "ADDED"
}
case class DeletedChange(example:CodeExample) extends Change {
  override val kind = "DELETED"
  override val publishedUrls: Map[String, String] = Map.empty
}
case class ChangeIssue(example:CodeExample) extends Change {
  override val kind = "NOT DONE!"
  override val publishedUrls: Map[String, String] = Map.empty
}
