package fr.janalyse.cem.model

import fr.janalyse.cem.tools.GitMetaData
import zio.json.*
import zio.lmdb.json.LMDBCodecJson

import java.time.OffsetDateTime

case class CodeExampleMetaData(
  gitMetaData: Option[GitMetaData],
  metaDataFileContentHash: String,
  metaDataLastUsed: OffsetDateTime // last seen/used date, useful for database garbage collection purposes
) derives LMDBCodecJson
