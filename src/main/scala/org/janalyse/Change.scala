package org.janalyse

sealed trait Change {
  val example:CodeExample
}

case class NoChange(example:CodeExample) extends Change
case class UpdatedChange(example:CodeExample) extends Change
case class AddedChange(example:CodeExample) extends Change
case class DeletedChange(example:CodeExample) extends Change
