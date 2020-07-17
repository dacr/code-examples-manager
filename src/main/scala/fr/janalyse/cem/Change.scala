package fr.janalyse.cem

import java.net.URL

sealed trait Change {
  val example:CodeExample
  val publishedUrl:Option[String]
  val kind:String
  override def toString: String =
    s"""$kind ${example.uuid.getOrElse("?")} - ${example.summary.getOrElse("?")} - ${publishedUrl.getOrElse("?")}"""
}

case class NoChange(example:CodeExample,publishedUrl:Option[String]=None) extends Change {
  override val kind = "UNCHANGED"
}
case class UpdatedChange(example:CodeExample,publishedUrl:Option[String]) extends Change {
  override val kind = "UPDATED"
}
case class AddedChange(example:CodeExample,publishedUrl:Option[String]) extends Change {
  override val kind = "ADDED"
}
case class DeletedChange(example:CodeExample) extends Change {
  override val kind = "DELETED"
  override val publishedUrl: Option[String] = None
}
case class ChangeIssue(example:CodeExample) extends Change {
  override val kind = "NOT DONE!"
  override val publishedUrl: Option[String] = None
}
