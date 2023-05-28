/*
 * Copyright 2023 David Crosson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.janalyse.cem.model

import fr.janalyse.cem.FileSystemService
import fr.janalyse.cem.tools.*
import fr.janalyse.cem.tools.Hashes.sha1
import zio.*
import zio.lmdb.*
import zio.nio.charset.Charset
import zio.nio.file.*
import zio.json.*

import java.io.File
import java.time.{Instant, OffsetDateTime, ZoneId}
import java.util.UUID

sealed trait ExampleIssue {
  def filepath: Path
}
case class ExampleContentIssue(filepath: Path, throwable: Throwable)                                       extends ExampleIssue
case class ExampleNoParentDirectory(filepath: Path)                                                        extends ExampleIssue
case class ExampleFilenameIssue(filepath: Path, throwable: Throwable)                                      extends ExampleIssue
case class ExampleIOIssue(filepath: Path, throwable: Throwable)                                            extends ExampleIssue
case class ExampleIdentifierNotFoundIssue(filepath: Path)                                                  extends ExampleIssue
case class ExampleUUIDIdentifierIssue(filepath: Path, id: String, throwable: Throwable)                    extends ExampleIssue
case class ExampleCreatedOnDateFormatIssue(filepath: Path, throwable: Throwable)                           extends ExampleIssue
case class ExampleGitIssue(filepath: Path, throwable: Throwable)                                           extends ExampleIssue
case class ExampleInvalidAttachmentFilename(filepath: Path, attachFilename: String)                        extends ExampleIssue
case class ExampleAttachmentContentIssue(filepath: Path, attachmentFilename: String, throwable: Throwable) extends ExampleIssue
case class ExampleStorageIssue(filepath: Path, message: String)                                            extends ExampleIssue

case class CodeExample(
  filepath: Option[Path],
  filename: String,
  content: String,
  hash: String,
  uuid: UUID,                                   // embedded
  category: Option[String] = None,              // optionally embedded - default value is containing directory
  createdOn: Option[OffsetDateTime] = None,     // embedded
  lastUpdated: Option[OffsetDateTime] = None,   // computed from file
  summary: Option[String] = None,               // embedded
  keywords: Set[String] = Set.empty,            // embedded
  publish: List[String] = Nil,                  // embedded
  authors: List[String] = Nil,                  // embedded
  runWith: Option[String] = None,               // embedded
  testWith: Option[String] = None,              // embedded
  managedBy: Option[String] = None,             // embedded
  license: Option[String] = None,               // embedded
  updatedCount: Option[Int] = None,             // computed from GIT history
  attachments: Map[String, String] = Map.empty, // embedded
  lastSeen: Option[OffsetDateTime] = None       // last seen/used date, useful for database garbage collection purposes
) {
  def fileExtension: String     = filename.split("[.]", 2).drop(1).headOption.getOrElse("")
  def isTestable: Boolean       = keywords.contains("@testable")
  def isExclusive: Boolean      = keywords.contains("@exclusive") // exclusive examples are executed sequentially
  def shouldFail: Boolean       = keywords.contains("@fail")
  def isPublishable: Boolean    = !publish.isEmpty
  override def toString: String = s"$category $filename $uuid $summary"
}

object CodeExample {
  given JsonEncoder[Path]        = JsonEncoder[String].contramap(p => p.toString)
  given JsonDecoder[Path]        = JsonDecoder[String].map(p => Path(p))
  given JsonDecoder[CodeExample] = DeriveJsonDecoder.gen
  given JsonEncoder[CodeExample] = DeriveJsonEncoder.gen

  def exampleContentExtractValue(from: String, key: String): Option[String] = {
    val RE = ("""(?m)(?i)^(?:(?:// )|(?:## )|(?:- )|(?:-- )) *""" + key + """ *: *(.*)$""").r
    RE.findFirstIn(from).collect { case RE(value) => value.trim }.filter(_.size > 0)
  }

  def exampleContentExtractValueList(from: String, key: String): List[String] = {
    exampleContentExtractValue(from, key).map(_.split("""\s*[,;]\s*""").toList).getOrElse(Nil)
  }

  def filenameFromFilepath(filepath: String): String = {
    new File(filepath).getName
  }

  def exampleCategoryFromFilepath(examplePath: Path, searchPath: Path): Option[String] = {
    examplePath.parent
      .map(parent => searchPath.relativize(parent))
      .map(_.toString)
      .filter(_.size > 0)
  }

  def fileLastModified(examplePath: Path) = {
    OffsetDateTime.ofInstant(Instant.ofEpochMilli(examplePath.toFile.lastModified), ZoneId.systemDefault())
  }

  def getAttachmentContent(examplePath: Path, attachmentFilename: String) = {
    val attachmentFilenameRE = "(?i)[-a-z0-9_][-a-z0-9_.]+"
    for {
      _                <- ZIO.cond(attachmentFilename.matches(attachmentFilenameRE), (), ExampleInvalidAttachmentFilename(examplePath, attachmentFilename))
      exampleDirectory <- ZIO.fromOption(examplePath.parent).mapError(_ => ExampleNoParentDirectory(examplePath))
      attachmentPath    = exampleDirectory / attachmentFilename
      content          <- FileSystemService.readFileContent(attachmentPath).mapError(th => ExampleAttachmentContentIssue(examplePath, attachmentFilename, th))
    } yield content
  }

  def getGitMetaData(examplePath: Path, content: String): ZIO[LMDB, ExampleIssue, Option[GitMetaData]] = {
    val collectionName = "code-examples-metadata"
    val exampleKey     = sha1(examplePath.toString)
    val contentHash    = sha1(content)

    val usingGitLogic =
      ZIO
        .attempt(GitOps.getGitFileMetaData(examplePath.toFile.toPath))
        .mapError(th => ExampleGitIssue(examplePath, th))

    for {
      collection          <- LMDB
                               .collectionGet[CodeExampleMetaData](collectionName)
                               .orElse(LMDB.collectionCreate[CodeExampleMetaData](collectionName))
                               .mapError(th => ExampleStorageIssue(examplePath, s"Storage issue with collection $collectionName"))
      foundMetaData       <- collection
                               .fetch(exampleKey)
                               .map(_.filter(_.metaDataFileContentHash == contentHash))
                               .mapError(th => ExampleStorageIssue(examplePath, s"Couldn't fetch anything from $collectionName"))
      gitMetaData         <- ZIO.from(foundMetaData.map(_.gitMetaData)).orElse(usingGitLogic)
      currentDateTime     <- Clock.currentDateTime
      updatedFoundMetaData = CodeExampleMetaData(
                               gitMetaData = gitMetaData,
                               metaDataFileContentHash = contentHash,
                               metaDataLastUsed = currentDateTime
                             )
      _                   <- collection
                               .upsertOverwrite(exampleKey, updatedFoundMetaData)
                               .mapError(th => ExampleStorageIssue(examplePath, s"Couldn't upsert anything in collection $collectionName"))
    } yield gitMetaData
  }

  def upsertExample(examplePath: Path, example: CodeExample) = {
    val collectionName = "code-examples"
    val exampleKey     = example.uuid.toString
    for {
      collection <- LMDB
                      .collectionGet[CodeExample](collectionName)
                      .orElse(LMDB.collectionCreate[CodeExample](collectionName))
                      .mapError(th => ExampleStorageIssue(examplePath, s"Storage issue with collection $collectionName"))
      _          <- collection
                      .upsertOverwrite(exampleKey, example)
                      .tapError(th => ZIO.logError(th.toString))
                      .mapError(th => ExampleStorageIssue(examplePath, s"Couldn't upsert anything in collection $collectionName"))
    } yield ()
  }

  def build(
    filepath: Option[Path],
    filename: String,
    content: String,
    uuid: UUID,                                   // embedded
    category: Option[String] = None,              // optionally embedded - default value is containing directory
    createdOn: Option[OffsetDateTime] = None,     // embedded
    lastUpdated: Option[OffsetDateTime] = None,   // computed from file
    summary: Option[String] = None,               // embedded
    keywords: Set[String] = Set.empty,            // embedded
    publish: List[String] = Nil,                  // embedded
    authors: List[String] = Nil,                  // embedded
    runWith: Option[String] = None,               // embedded
    testWith: Option[String] = None,              // embedded
    managedBy: Option[String] = None,             // embedded
    license: Option[String] = None,               // embedded
    updatedCount: Option[Int] = None,             // computed from GIT history
    attachments: Map[String, String] = Map.empty, // embedded
    lastSeen: Option[OffsetDateTime] = None       // last seen/used date, useful for database garbage collection purposes
  ): CodeExample = {
    val hash = sha1(content + filename + category.getOrElse("") + attachments.keys.mkString + attachments.values.mkString)
    CodeExample(
      uuid = uuid,
      hash = hash,
      content = content,
      filename = filename,
      filepath = filepath,
      category = category,
      createdOn = createdOn,
      lastUpdated = lastUpdated,
      updatedCount = updatedCount,
      summary = summary,
      keywords = keywords,
      publish = publish,
      authors = authors,
      runWith = runWith,
      testWith = testWith,
      managedBy = managedBy,
      license = license,
      attachments = attachments,
      lastSeen = lastSeen
    )
  }

  def buildFromFile(
    examplePath: Path,
    fromSearchPath: Path
  ): ZIO[FileSystemService & LMDB, ExampleIssue, CodeExample] = {
    for {
      filename        <- ZIO
                           .getOrFail(Option(examplePath.filename).map(_.toString))
                           .mapError(th => ExampleFilenameIssue(examplePath, th))
      givenContent    <- FileSystemService.readFileContent(examplePath).mapError(th => ExampleContentIssue(examplePath, th))
      content          = givenContent.replaceAll("\r", "")
      category         = exampleContentExtractValue(content, "category").orElse(exampleCategoryFromFilepath(examplePath, fromSearchPath))
      foundId          = exampleContentExtractValue(content, "id")
      foundCreatedOn   = exampleContentExtractValue(content, "created-on")
      id              <- ZIO
                           .getOrFail(foundId)
                           .mapError(th => ExampleIdentifierNotFoundIssue(examplePath))
      uuid            <- ZIO
                           .attempt(UUID.fromString(id))
                           .mapError(th => ExampleUUIDIdentifierIssue(examplePath, id, th))
      gitMetaData     <- getGitMetaData(examplePath, givenContent)
      createdOn       <- ZIO
                           .attempt(foundCreatedOn.map(OffsetDateTime.parse))
                           .mapAttempt(_.orElse(gitMetaData.map(_.createdOn)))
                           .mapError(th => ExampleCreatedOnDateFormatIssue(examplePath, th))
      lastUpdated     <- ZIO
                           .attempt(gitMetaData.map(_.lastUpdated))
                           .mapAttempt(_.getOrElse(fileLastModified(examplePath)))
                           .mapError(th => ExampleIOIssue(examplePath, th))
      updatedCount     = gitMetaData.map(_.changesCount)
      attachmentsNames = exampleContentExtractValueList(content, "attachments")
      attachments     <- ZIO.foreach(attachmentsNames)(name => getAttachmentContent(examplePath, name).map(content => name -> content)).map(_.toMap)
      currentDate     <- Clock.currentDateTime
      example          = build(
                           uuid = uuid,
                           content = content,
                           filename = filename,
                           filepath = Some(examplePath),
                           category = category,
                           createdOn = createdOn,
                           lastUpdated = Some(lastUpdated),
                           updatedCount = updatedCount,
                           summary = exampleContentExtractValue(content, "summary"),
                           keywords = exampleContentExtractValueList(content, "keywords").map(_.trim).filter(_.size > 0).toSet,
                           publish = exampleContentExtractValueList(content, "publish"),
                           authors = exampleContentExtractValueList(content, "authors"),
                           runWith = exampleContentExtractValue(content, "run-with"),
                           testWith = exampleContentExtractValue(content, "test-with"),
                           managedBy = exampleContentExtractValue(content, "managed-by"),
                           license = exampleContentExtractValue(content, "license"),
                           attachments = attachments,
                           lastSeen = Some(currentDate)
                         )
      _               <- upsertExample(examplePath, example)
    } yield example
  }

}
