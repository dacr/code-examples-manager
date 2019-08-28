package org.janalyse.externalities.github

import com.softwaremill.sttp.json4s.asJson
import com.softwaremill.sttp._
import com.softwaremill.sttp.json4s._
import org.janalyse.{AddedChange, Change, CodeExample, NoChange, UpdatedChange}
import org.janalyse.externalities.{AuthToken, PublishAdapter}
import org.json4s.JValue
import org.slf4j.{Logger, LoggerFactory}

import scala.util.{Left, Right}

class GitHubPublishAdapter extends PublishAdapter {
  implicit val serialization = org.json4s.native.Serialization
  implicit val formats = org.json4s.DefaultFormats
  implicit val sttpBackend = com.softwaremill.sttp.okhttp.OkHttpSyncBackend()

  private val logger:Logger = LoggerFactory.getLogger(getClass)

  private def makeGetRequest(query: Uri)(implicit token: AuthToken) = {
    sttp
      .get(query)
      .header("Authorization", s"token $token")
  }

  def getUser()(implicit token:AuthToken):Option[GistUser] = {
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

  def userGists(user: GistUser)(implicit token: AuthToken): Stream[GistInfo] = {
    val nextLinkRE = """.*<([^>]+)>; rel="next".*""".r

    def worker(nextQuery: Option[Uri], currentRemaining: Iterable[GistInfo]): Stream[GistInfo] = {
      (nextQuery, currentRemaining) match {
        case (None, Nil) => Stream.empty
        case (_, head :: tail) => head #:: worker(nextQuery, tail)
        case (Some(query), Nil) =>
          val response = {
            sttp
              .get(query)
              .header("Authorization", s"token $token")
              .response(asJson[Array[GistInfo]])
              .send()
          }
          response.body match {
            case Left(message) =>
              logger.error(s"List gists - Something wrong has happened : $message")
              Stream.empty
            case Right(gistsArray) =>
              val next = response.header("Link") // it provides the link for the next & last page :)
              val newNextQuery = next.collect { case nextLinkRE(uri) => uri"$uri" }
              worker(newNextQuery, gistsArray.toList)
          }
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
      sttp
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
      sttp
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
      sttp
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

  override def synchronize(examples: List[CodeExample], authToken: AuthToken): List[Change] = {
    implicit val authTokenMadeImplicit = authToken
    getUser match {
      case None =>
        logger.warn(s"Can't get user information, check token roles, read:user must be enabled")
        List.empty
      case Some(user) =>
        val remoteGistInfosByUUID =
          userGists(user)
          .collect{ case gistInfo if gistInfo.uuid.isDefined => gistInfo.uuid.get -> gistInfo}
          .toMap

        val result = for {
          example <- examples
          uuid <- example.uuid
          checksum = example.checksum
        } yield {
          val gistFileSpec = GistFileSpec(
            filename = example.file.name,
            content = example.content
          )
          val description = GistInfo.makeDescription(example.summary.get, uuid, checksum)
          val gist = GistSpec(
            description = description,
            public = true, // TODO to implement
            files = Map(example.file.name -> gistFileSpec)
          )
          remoteGistInfosByUUID.get(uuid) match {
            case Some(remoteGist) =>
              val rc = updateGist(remoteGist.id, gist)
              if (rc.isDefined) UpdatedChange(example) else NoChange(example)
            case None =>
              val rc = addGist(gist)
              if (rc.isDefined) AddedChange(example) else NoChange(example)
          }
        }
        // TODO : remove not anymore UUID from remote gists
        result
    }

  }
}
