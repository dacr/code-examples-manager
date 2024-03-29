package fr.janalyse.cem.model

import zio.json.*
import java.util.UUID
import java.time.OffsetDateTime

case class RunStatus(
  example: CodeExample,
  exitCodeOption: Option[Int],
  stdout: String,
  startedTimestamp: OffsetDateTime,
  duration: Long,
  runSessionDate: OffsetDateTime,
  runSessionUUID: UUID,
  success: Boolean,
  timeout: Boolean,
  runState: String
) derives JsonCodec

