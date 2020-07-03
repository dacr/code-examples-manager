package org.janalyse.externalities.github

import sttp.client._
import sttp.client.json4s.asJson
import sttp.client.json4s._
import org.janalyse.{AddedChange, Change, ChangeIssue, CodeExample, NoChange, UpdatedChange}
import org.janalyse.externalities.{AuthToken, PublishAdapter}
import org.json4s.JValue
import org.slf4j.{Logger, LoggerFactory}
import sttp.model.Uri

import scala.util.{Left, Right}

class GithubPublishAdapter extends PublishAdapter {
  implicit val serialization = org.json4s.native.Serialization
  implicit val formats = org.json4s.DefaultFormats
  implicit val sttpBackend = sttp.client.okhttp.OkHttpSyncBackend()

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  val gistKeyword = "gist"

  private def makeGetRequest(query: Uri)(implicit token: AuthToken) = {
    basicRequest
      .get(query)
      .header("Authorization", s"token $token")
  }

  def getUser()(implicit token: AuthToken): Option[GistUser] = {
    val query = uri"https://api.github.com/user"
    val response = makeGetRequest(query).response(asJson[GistUser]).send()
    response.body match {
      case Left(message) =>
        logger.error(s"Get authenticated user information - Something wrong has happened : $message")
        None
      case Right(gist) =>
        Some(gist)
    }

  }

  def userGists(user: GistUser)(implicit token: AuthToken): LazyList[GistInfo] = {
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
            case Left(message) =>
              logger.error(s"List gists - Something wrong has happened : $message")
              LazyList.empty
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
    val startQuery = uri"https://api.github.com/users/$userLogin/gists?page=1&per_page=$count"
    worker(Some(startQuery), Nil)
  }


  def getGist(id: String)(implicit token: AuthToken): Option[Gist] = {
    val query = uri"https://api.github.com/gists/$id"
    val response = {
      basicRequest
        .get(query)
        .header("Authorization", s"token $token")
        .response(asJson[Gist])
        .send()
    }
    response.body match {
      case Left(message) =>
        logger.error(s"Get gist - Something wrong has happened : $message")
        None
      case Right(gist) =>
        Some(gist)
    }
  }


  def addGist(gist: GistSpec)(implicit token: AuthToken): Option[String] = {
    val query = uri"https://api.github.com/gists"
    val response = {
      basicRequest
        .body(gist)
        .post(query)
        .header("Authorization", s"token $token")
        .response(asJson[JValue])
        .send()
    }
    response.body match {
      case Left(message) =>
        logger.error(s"Add gist - Something wrong has happened : $message")
        None
      case Right(jvalue) =>
        (jvalue \ "id").extractOpt[String]
    }
  }

  def updateGist(id: String, gist: GistSpec)(implicit token: AuthToken): Option[String] = {
    val query = uri"https://api.github.com/gists/$id"
    val response = {
      basicRequest
        .body(gist)
        .patch(query)
        .header("Authorization", s"token $token")
        .response(asJson[JValue])
        .send()
    }
    response.body match {
      case Left(message) =>
        logger.error(s"Update gist - Something wrong has happened : $message")
        None
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
   * @param authToken authentication token with gist and read:user credentials
   * @return list of the applied changes
   */
  override def synchronize(examples: List[CodeExample], authToken: AuthToken): List[Change] = {
    val examplesForGithub = examples.filter(_.publish.contains(gistKeyword))
    implicit val authTokenMadeImplicit: AuthToken = authToken
    getUser() match {
      case None =>
        logger.warn(s"Can't get user information, check token roles, read:user must be enabled")
        List.empty
      case Some(user) =>
        val remoteGistInfosByUUID = getRemoteGistInfosByUUID(user)

        val result = for {
          example <- examplesForGithub
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

  private def synchronizeExample(example: CodeExample, gist: GistSpec, checksum: String, remoteGistInfo: Option[GistInfo])(implicit token: AuthToken): Change = {
    remoteGistInfo match {
      case Some(remoteGist) if remoteGist.checksumOption.contains(checksum) => synchronizeNoChange(example, remoteGist)
      case Some(remoteGist) => synchronizeUpdate(example, gist, remoteGist)
      case None => synchronizeAdd(example, gist)
    }
  }

  private def synchronizeNoChange(example: CodeExample, remoteGist: GistInfo)(implicit authToken: AuthToken) = {
    if (remoteGist.files.size > 1) logger.warn(s"${remoteGist.html_url} has more than one file}")
    NoChange(example, Map(gistKeyword -> remoteGist.html_url))
  }

  private def synchronizeAdd(example: CodeExample, gist: GistSpec)(implicit authToken: AuthToken): Change = {
    val rc = addGist(gist)
    if (rc.isDefined) {
      val newGist = getGist(rc.get)
      newGist
        .map(g => AddedChange(example, Map(gistKeyword -> g.html_url)))
        .getOrElse(ChangeIssue(example))
    } else
      ChangeIssue(example)
  }

  private def synchronizeUpdate(example: CodeExample, gist: GistSpec, remoteGist: GistInfo)(implicit authToken: AuthToken): Change = {
    if (remoteGist.files.size > 1) logger.warn(s"${remoteGist.html_url} has more than one file}")
    val rc = updateGist(remoteGist.id, gist)
    if (rc.isDefined)
      UpdatedChange(example, Map(gistKeyword -> remoteGist.html_url))
    else
      ChangeIssue(example)
  }

  private def getRemoteGistInfosByUUID(user: GistUser)(implicit authToken: AuthToken): Map[String, GistInfo] = {
    userGists(user)
      .collect { case gistInfo if gistInfo.uuidOption.isDefined => gistInfo.uuidOption.get -> gistInfo }
      .toMap
  }

  override def exampleUpsert(example: CodeExample, authToken: AuthToken): Change = {
    implicit val authTokenMadeImplicit: AuthToken = authToken
    getUser() match {
      case None =>
        logger.warn(s"Can't get user information, check token roles, read:user must be enabled")
        NoChange(example, Map.empty)
      case Some(user) =>
        val remoteGistInfosByUUID = getRemoteGistInfosByUUID(user)
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
