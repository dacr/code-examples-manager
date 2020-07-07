package fr.janalyse.cem.externalities.publishadapter.github

import fr.janalyse.cem.{AddedChange, Change, ChangeIssue, CodeExample, NoChange, PublishAdapterConfig, UpdatedChange}
import sttp.client._
import sttp.client.json4s.asJson
import sttp.client.json4s._
import fr.janalyse.cem.CodeExample
import fr.janalyse.cem.externalities.publishadapter.{AuthToken, PublishAdapter}
import org.json4s.JValue
import org.slf4j.{Logger, LoggerFactory}
import sttp.model.Uri

import scala.util.{Left, Right}

object GithubPublishAdapter {
  def lookup(config:PublishAdapterConfig):Option[PublishAdapter] = {
    if (config.enabled && config.authToken.isDefined) Some(new GithubPublishAdapter(config)) else None
  }
}

class GithubPublishAdapter(val config:PublishAdapterConfig) extends PublishAdapter {
  implicit val serialization = org.json4s.native.Serialization
  implicit val formats = org.json4s.DefaultFormats
  implicit val sttpBackend = sttp.client.okhttp.OkHttpSyncBackend()

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  val token = config.authToken.getOrElse("")
  val apiUrl = config.apiEndPoint

  private def makeGetRequest(query: Uri) = {
    basicRequest
      .get(query)
      .header("Authorization", s"token $token")
  }

  def getUser(): Option[GistUser] = {
    val query = uri"$apiUrl/user"
    val response = makeGetRequest(query).response(asJson[GistUser]).send()
    response.body match {
      case Left(responseException) =>
        logger.error(s"Get authenticated user information - Something wrong has happened ", responseException)
        throw responseException
      case Right(user) =>
        Some(user)
    }

  }

  def userGists(user: GistUser): LazyList[GistInfo] = {
    val nextLinkRE = """.*<([^>]+)>; rel="next".*""".r

    def worker(nextQuery: Option[Uri], currentRemaining: Iterable[GistInfo]): LazyList[GistInfo] = {
      (nextQuery, currentRemaining) match {
        case (None, Nil) => LazyList.empty
        case (_, head :: tail) => head #:: worker(nextQuery, tail)
        case (Some(query), Nil) =>
          val response = {
            basicRequest
              .get(query)
              .header("Authorization", s"token $token")
              .response(asJson[Array[GistInfo]])
              .send()
          }
          response.body match {
            case Left(responseException) =>
              logger.error(s"List gists - Something wrong has happened", responseException)
              throw responseException
            case Right(gistsArray) =>
              val next = response.header("Link") // it provides the link for the next & last page :)
              val newNextQuery = next.collect { case nextLinkRE(uri) => uri"$uri" }
              worker(newNextQuery, gistsArray.toList)
          }
        case other =>
          logger.warn("Not understandable response : " + other.toString())
          LazyList.empty
      }
    }

    val count = 10
    val userLogin = user.login
    val startQuery = uri"$apiUrl/users/$userLogin/gists?page=1&per_page=$count"
    worker(Some(startQuery), Nil)
  }


  def getGist(id: String): Option[Gist] = {
    val query = uri"$apiUrl/gists/$id"
    val response = {
      basicRequest
        .get(query)
        .header("Authorization", s"token $token")
        .response(asJson[Gist])
        .send()
    }
    response.body match {
      case Left(responseException) =>
        logger.error(s"Get gist - Something wrong has happened", responseException)
        throw responseException
      case Right(gist) =>
        Some(gist)
    }
  }


  def addGist(gist: GistSpec): Option[String] = {
    val query = uri"$apiUrl/gists"
    val response = {
      basicRequest
        .body(gist)
        .post(query)
        .header("Authorization", s"token $token")
        .response(asJson[JValue])
        .send()
    }
    response.body match {
      case Left(responseException) =>
        logger.error(s"Add gist - Something wrong has happened", responseException)
        throw responseException
      case Right(jvalue) =>
        (jvalue \ "id").extractOpt[String]
    }
  }

  def updateGist(id: String, gist: GistSpec): Option[String] = {
    val query = uri"$apiUrl/gists/$id"
    val response = {
      basicRequest
        .body(gist)
        .patch(query)
        .header("Authorization", s"token $token")
        .response(asJson[JValue])
        .send()
    }
    response.body match {
      case Left(responseException) =>
        logger.error(s"Update gist - Something wrong has happened", responseException)
        throw responseException
      case Right(jvalue) =>
        (jvalue \ "id").extractOpt[String]
    }
  }


  def makeGistSpec(example: CodeExample): Option[GistSpec] = {
    for {
      uuid <- example.uuid
      summary <- example.summary
      checksum = example.checksum
      filename = example.filename
      content = example.content
    } yield {
      val gistFileSpec = GistFileSpec(
        filename = filename,
        content = content
      )
      val description = GistInfo.makeDescription(summary, uuid, checksum)
      GistSpec(
        description = description,
        public = true,
        files = Map(filename -> gistFileSpec)
      )
    }
  }


  /**
   * Synchronize github examples
   *
   * @param examples  examples to synchronize
   * @return list of the applied changes
   */
  override def synchronize(examples: List[CodeExample]): List[Change] = {
    getUser() match {
      case None =>
        logger.warn(s"Can't get user information, check token roles, read:user must be enabled")
        List.empty
      case Some(user) =>
        val remoteGistInfosByUUID = getRemoteGistInfosByUUID(user)

        val result = for {
          example <- examples
          uuid <- example.uuid
          gist <- makeGistSpec(example)
          checksum = example.checksum
          remoteGistInfo = remoteGistInfosByUUID.get(uuid)
        } yield {
          synchronizeExample(example, gist, checksum, remoteGistInfo)
        }
        // TODO : remove not anymore UUID from remote gists - but take care if same publish target is used multiple times
        result
    }

  }

  private def synchronizeExample(example: CodeExample, gist: GistSpec, checksum: String, remoteGistInfo: Option[GistInfo]): Change = {
    remoteGistInfo match {
      case Some(remoteGist) if remoteGist.checksumOption.contains(checksum) => synchronizeNoChange(example, remoteGist)
      case Some(remoteGist) => synchronizeUpdate(example, gist, remoteGist)
      case None => synchronizeAdd(example, gist)
    }
  }

  private def synchronizeNoChange(example: CodeExample, remoteGist: GistInfo) = {
    if (remoteGist.files.size > 1) logger.warn(s"${remoteGist.html_url} has more than one file}")
    NoChange(example, Some(remoteGist.html_url))
  }

  private def synchronizeAdd(example: CodeExample, gist: GistSpec): Change = {
    val rc = addGist(gist)
    if (rc.isDefined) {
      val newGist = getGist(rc.get)
      newGist
        .map(g => AddedChange(example, Some(g.html_url)))
        .getOrElse(ChangeIssue(example))
    } else
      ChangeIssue(example)
  }

  private def synchronizeUpdate(example: CodeExample, gist: GistSpec, remoteGist: GistInfo): Change = {
    if (remoteGist.files.size > 1) logger.warn(s"${remoteGist.html_url} has more than one file}")
    val rc = updateGist(remoteGist.id, gist)
    if (rc.isDefined)
      UpdatedChange(example, Some(remoteGist.html_url))
    else
      ChangeIssue(example)
  }

  private def getRemoteGistInfosByUUID(user: GistUser): Map[String, GistInfo] = {
    userGists(user)
      .collect { case gistInfo if gistInfo.uuidOption.isDefined => gistInfo.uuidOption.get -> gistInfo }
      .toMap
  }

  override def exampleUpsert(example: CodeExample): Change = {
    getUser() match {
      case None =>
        logger.warn(s"Can't get user information, check token roles, read:user must be enabled")
        NoChange(example)
      case Some(user) =>
        val remoteGistInfosByUUID = getRemoteGistInfosByUUID(user) // TODO : RELOADED !
        val result = for {
          uuid <- example.uuid
          gist <- makeGistSpec(example)
          checksum = example.checksum
          remoteGistInfo = remoteGistInfosByUUID.get(uuid)
        } yield {
          synchronizeExample(example, gist, checksum, remoteGistInfo)
        }
        result.getOrElse(ChangeIssue(example))
    }
  }
}
