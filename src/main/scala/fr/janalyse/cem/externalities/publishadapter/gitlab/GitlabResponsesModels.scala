package fr.janalyse.cem.externalities.publishadapter.gitlab

import java.time.OffsetDateTime

case class SnippetAuthor(
  id: Long,
  name: String,
  username: String,
  state: String,
  avatarUrl: String,
  webUrl: String
)

case class SnippetInfo(
  id: Long,
  title: String,
  fileName: String,
  description: String,
  visibility: String,
  author: SnippetAuthor,
  updatedAt: OffsetDateTime,
  createdAt: OffsetDateTime,
  webUrl: String,
  rawUrl: String,
)

case class Snippet(
  title: String,
  fileName: String,
  content: String,
  description: String,
  visibility:String,
)

case class SnippetUpdate(
  id: Long,
  title: String,
  fileName: String,
  content: String,
  description: String,
  visibility:String,
)