package fr.janalyse.cem.tools

import zio.json.*
import java.time.{Instant, OffsetDateTime, ZoneId}

case class GitMetaData(
  changesCount: Int,
  createdOn: OffsetDateTime,
  lastUpdated: OffsetDateTime
)

object GitMetaData {
  given JsonCodec[GitMetaData] = DeriveJsonCodec.gen
}
