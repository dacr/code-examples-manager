package fr.janalyse.cem.externalities.publishadapter.gitlab

import fr.janalyse.cem.{AddedChange, Change, CodeExample, NoChange, PublishAdapterConfig, UpdatedChange}
import fr.janalyse.cem.externalities.publishadapter.{AuthToken, PublishAdapter}
import org.slf4j.{Logger, LoggerFactory}
import sttp.client._
import org.json4s.JValue
import org.json4s.Extraction.decompose
import org.json4s.native.Serialization.write
import org.json4s.ext.JavaTimeSerializers
import sttp.client.json4s.asJson
import sttp.client.json4s._
import sttp.model.Uri

import scala.util.{Left, Right}

object GitlabPublishAdapter {
  def lookup(config: PublishAdapterConfig): Option[PublishAdapter] = {
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
  val defaultVisibility = config.defaultVisibility.getOrElse("public")


  def listSnippets(): LazyList[SnippetInfo] = {
    val nextLinkRE = """.*<([^>]+)>; rel="next".*""".r

    def worker(nextQuery: Option[Uri], currentRemaining: Iterable[SnippetInfo]): LazyList[SnippetInfo] = {
      (nextQuery, currentRemaining) match {
        case (None, Nil) => LazyList.empty
        case (_, head :: tail) => head #:: worker(nextQuery, tail)
        case (Some(query), Nil) =>
          val response = {
            basicRequest
              .get(query)
              .header("PRIVATE-TOKEN", s"$token")
              .response(asJson[JValue])
              .send()
          }
          response.body match {
            case Left(responseException) =>
              logger.error(s"List gists - Something wrong has happened", responseException)
              throw responseException
            case Right(snippets) =>
              val next = response.header("link") // it provides the link for the next & last page :)
              val newNextQuery = next.collect { case nextLinkRE(uri) => uri"$uri" }
              worker(newNextQuery, snippets.camelizeKeys.extract[List[SnippetInfo]])
          }
        case other =>
          logger.warn("Not understandable response : " + other.toString())
          LazyList.empty
      }
    }

    val count = 20
    val startQuery = uri"$apiUrl/snippets?page=1&per_page=$count"
    worker(Some(startQuery), Nil)
  }

  def getSnippet(id: Int): Option[SnippetInfo] = {
    val query = uri"$apiUrl/snippets/$id"
    val response =
      basicRequest
        .get(query)
        .header("PRIVATE-TOKEN", s"$token")
        .response(asJson[JValue])
        .send()
    response.body match {
      case Left(responseException) =>
        logger.error(s"Get snippet $id - Something wrong has happened", responseException)
        throw responseException
      case Right(snippet) =>
        snippet.camelizeKeys.extractOpt[SnippetInfo]
    }
  }

  def createSnippet(snippet: Snippet): Option[SnippetInfo] = {
    val query = uri"$apiUrl/snippets"
    val body = decompose(snippet).snakizeKeys
    val response =
      basicRequest
        .post(query)
        .body(body)
        .header("PRIVATE-TOKEN", s"$token")
        .response(asJson[JValue])
        .send()
    response.body match {
      case Left(responseException) =>
        logger.error(s"List snippets - Something wrong has happened", responseException)
        throw responseException
      case Right(snippet) =>
        snippet.camelizeKeys.extractOpt[SnippetInfo]
    }

  }

  def updateSnippet(snippet: SnippetUpdate): Option[SnippetInfo] = {
    val id = snippet.id
    val query = uri"$apiUrl/snippets/$id"
    val body = decompose(snippet).snakizeKeys
    val response =
      basicRequest
        .put(query)
        .body(body)
        .header("PRIVATE-TOKEN", s"$token")
        .response(asJson[JValue])
        .send()
    response.body match {
      case Left(responseException) =>
        logger.error(s"Update snippet $id - Something wrong has happened", responseException)
        throw responseException
      case Right(snippet) =>
        snippet.camelizeKeys.extractOpt[SnippetInfo]
    }
  }

//  def deleteSnippet(id: Int): Boolean = {
//    val query = uri"$apiUrl/snippets/$id"
//    val response =
//      basicRequest
//        .delete(query)
//        .header("PRIVATE-TOKEN", s"$token")
//        .send()
//    response.body match {
//      case Left(responseException) =>
//        logger.error(s"Delete snippet $id - Something wrong has happened", responseException)
//        throw responseException
//      case Right(_) =>
//        response.code.isSuccess
//    }
//  }

  def getRemoteSnippetsInfosByUUID(): Map[String, SnippetInfo] = {
    val snippets = listSnippets()
    val groupedSnippets =
      snippets
        .toList
        .groupBy(_.uuidOption)
    val remoteDuplicates = groupedSnippets.collect {case (key,snippets) if snippets.size > 1 => snippets}
    assert(remoteDuplicates.size == 0, "FOUND REMOTE DUPLICATES !\n"+remoteDuplicates.mkString("\n"))
    groupedSnippets.collect { case (Some(uuid), example :: Nil) => uuid -> example }
  }

  def synchronizeNoChange(example: CodeExample, remoteSnippetInfo: SnippetInfo): Change = {
    NoChange(example, Some(remoteSnippetInfo.webUrl))
  }

  def synchronizeUpdate(example: CodeExample, remoteSnippetInfo: SnippetInfo): Change = {
    val snippetInfoOption = for {
      uuid <- example.uuid
      summary <- example.summary
      checksum = example.checksum
      filename = example.filename
      content = example.content
      description = Snippet.makeDescription(summary, uuid, checksum)
      id = remoteSnippetInfo.id
      updatedSnippet = SnippetUpdate(id = id, title = summary, fileName = filename, content = content, description = description, visibility = defaultVisibility)
      snippetInfo <- updateSnippet(updatedSnippet)
    } yield {
      snippetInfo
    }
    snippetInfoOption
      .map { snippetInfo => UpdatedChange(example, Some(snippetInfo.webUrl)) }
      .getOrElse(NoChange(example))
  }

  def synchronizeAdd(example: CodeExample): Change = {
    val snippetInfoOption = for {
      uuid <- example.uuid
      summary <- example.summary
      checksum = example.checksum
      filename = example.filename
      content = example.content
      description = Snippet.makeDescription(summary, uuid, checksum)
      snippet = Snippet(title = summary, content = content, fileName = filename, description = description, visibility = defaultVisibility)
      snippetInfo <- createSnippet(snippet)
    } yield {
      snippetInfo
    }

    snippetInfoOption
      .map { snippetInfo => AddedChange(example, Some(snippetInfo.webUrl)) }
      .getOrElse(NoChange(example))
  }

  private def synchronizeExample(example: CodeExample, checksum: String, remoteSnippetInfoOption: Option[SnippetInfo]): Change = {
    remoteSnippetInfoOption match {
      case Some(remoteSnippetInfo) if remoteSnippetInfo.checksumOption.contains(checksum) => synchronizeNoChange(example, remoteSnippetInfo)
      case Some(remoteSnippetInfo) => synchronizeUpdate(example, remoteSnippetInfo)
      case None => synchronizeAdd(example)
    }
  }

  override def synchronize(examples: List[CodeExample]): List[Change] = {
    val remoteSnippetsInfosByUUID = getRemoteSnippetsInfosByUUID()

    val result = for {
      example <- examples
      uuid <- example.uuid
      checksum = example.checksum
      remoteGistInfo = remoteSnippetsInfosByUUID.get(uuid)
    } yield {
      synchronizeExample(example, checksum, remoteGistInfo)
    }
    // TODO : remove not anymore UUID from remote gists - but take care if same publish target is used multiple times
    result
  }

  override def exampleUpsert(example: CodeExample): Change = {
    val remoteSnippetsInfosByUUID = getRemoteSnippetsInfosByUUID() // TODO : RELOADED !
    val result = for {
      uuid <- example.uuid
      checksum = example.checksum
      remoteGistInfo = remoteSnippetsInfosByUUID.get(uuid)
    } yield {
      synchronizeExample(example, checksum, remoteGistInfo)
    }
    result.getOrElse(NoChange(example))
  }
}
