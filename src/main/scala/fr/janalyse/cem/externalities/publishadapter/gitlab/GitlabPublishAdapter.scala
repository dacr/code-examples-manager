package fr.janalyse.cem.externalities.publishadapter.gitlab

import fr.janalyse.cem.{Change, CodeExample, PublishAdapterConfig}
import fr.janalyse.cem.CodeExample
import fr.janalyse.cem.externalities.publishadapter.{AuthToken, PublishAdapter}
import org.slf4j.{Logger, LoggerFactory}
import sttp.client._
import org.json4s.JValue
import org.json4s.ext.JavaTimeSerializers
import sttp.client.json4s.asJson
import sttp.client.json4s._
import sttp.model.Uri

import scala.util.{Left, Right}

object GitlabPublishAdapter {
  def lookup(config:PublishAdapterConfig):Option[PublishAdapter] = {
    if (config.enabled && config.authToken.isDefined) Some(new GitlabPublishAdapter(config)) else None

  }
}

class GitlabPublishAdapter(val config: PublishAdapterConfig) extends PublishAdapter {
  implicit val serialization = org.json4s.native.Serialization
  implicit val formats = org.json4s.DefaultFormats.lossless ++ JavaTimeSerializers.all
  implicit val sttpBackend = sttp.client.okhttp.OkHttpSyncBackend()

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  val token = config.authToken.getOrElse("")
  val apiUrl = config.apiEndPoint



  def listSnippets():Seq[SnippetInfo] = {
    val query = uri"$apiUrl/snippets"
    val response =
      basicRequest
        .get(query)
        .header("PRIVATE-TOKEN", s"$token")
        .response(asJson[JValue])
        .send()
    response.body match {
      case Left(responseException) =>
        logger.error(s"List snippets - Something wrong has happened", responseException)
        Seq.empty
      case Right(snippets) =>
        snippets.camelizeKeys.extract[Array[SnippetInfo]]
    }
  }

  def getSnippet(id:Int):Option[SnippetInfo] = {
    val query = uri"$apiUrl/snippets/$id"
    val response =
      basicRequest
        .get(query)
        .header("PRIVATE-TOKEN", s"$token")
        .response(asJson[SnippetInfo])
        .send()
    response.body match {
      case Left(message) =>
        logger.error(s"Get snippet $id - Something wrong has happened : $message")
        None
      case Right(snippet) =>
        Option(snippet)
    }
  }
  def updateSnippet(snippet:SnippetUpdate):Option[SnippetInfo] = {
    ???
  }
  def deleteSnippet(id: Int):Unit = {
    ???
  }


  override def synchronize(examples: List[CodeExample]): List[Change] = ???

  override def exampleUpsert(example: CodeExample): Change = ???
}
