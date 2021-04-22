package fr.janalyse.cem.tools

import fr.janalyse.cem.model.CodeExample

object DescriptionTools {
  private val metaDataRE = """#\s*([-0-9a-f]+)\s*/\s*([0-9a-f]+)\s*$""".r.unanchored

  def extractMetaDataFromDescription(description: String): Option[(String, String)] = {
    metaDataRE
      .findFirstMatchIn(description)
      .filter(_.groupCount == 2)
      .map(m => (m.group(1), m.group(2)))
  }

  def makeDescription(example: CodeExample): Option[String] = {
    for {
      summary <- example.summary
      uuid <- example.uuid
      chksum = example.checksum
      cemURL = "https://github.com/dacr/code-examples-manager"
    } yield s"$summary / published by $cemURL #$uuid/$chksum"
  }
}
