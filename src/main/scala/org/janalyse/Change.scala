package org.janalyse

sealed trait Change {
  val example:CodeExample
  val kind:String
  override def toString: String =
    s"""$kind ${example.uuid.getOrElse("?")} - ${example.summary.getOrElse("?")}"""
}

case class NoChange(example:CodeExample) extends Change {
  override val kind = "UNCHANGED"
}
case class UpdatedChange(example:CodeExample) extends Change {
  override val kind = "UPDATED"
}
case class AddedChange(example:CodeExample) extends Change {
  override val kind = "ADDED"
}
case class DeletedChange(example:CodeExample) extends Change {
  override val kind = "DELETED"
}
case class ChangeIssue(example:CodeExample) extends Change {
  override val kind = "NOT DONE!"
}
