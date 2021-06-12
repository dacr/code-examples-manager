package fr.janalyse.cem.model

case class RemoteExampleState(
    remoteId: String,
    description: String,
    url: String,
    filename: Option[String],
    uuid: String,
    checksum: String
)
