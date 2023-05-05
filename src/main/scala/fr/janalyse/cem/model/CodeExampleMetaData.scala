package fr.janalyse.cem.model

import fr.janalyse.cem.tools.GitMetaData
import zio.json.*

import java.time.OffsetDateTime

case class CodeExampleMetaData(
  gitMetaData: Option[GitMetaData],
  metaDataFileContentHash: String,
  metaDataLastUsed: OffsetDateTime
) derives JsonCodec
