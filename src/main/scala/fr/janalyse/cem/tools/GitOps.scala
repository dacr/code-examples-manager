package fr.janalyse.cem.tools

import java.io.File
import java.nio.file.Path
import java.time.{Instant, OffsetDateTime, ZoneId}
import scala.util.Properties

object GitOps {
  import org.eclipse.jgit.storage.file.FileRepositoryBuilder
  import org.eclipse.jgit.api.Git
  import org.eclipse.jgit.lib.Repository
  import org.eclipse.jgit.lib.Constants
  import org.eclipse.jgit.revwalk.RevCommit
  import scala.jdk.CollectionConverters.*

  def getLocalRepositoryFromFile(fromFile: File, ceilingDirectoryOption: Option[File] = None): Option[Repository] =
    val builder = new FileRepositoryBuilder()
    builder.setMustExist(true)
    ceilingDirectoryOption.foreach(ceilingDirectory => builder.addCeilingDirectory(ceilingDirectory))
    builder.findGitDir(fromFile)
    Option(builder.getGitDir()).map(_ => builder.build())

  def getFileLog(git: Git, file: File, revision: String = Constants.HEAD): List[RevCommit] =
    val repository     = git.getRepository
    val repoHomeDir    = repository.getDirectory.toPath.getParent
    val revisionId     = repository.resolve(Constants.HEAD)
    val targetFile     = repoHomeDir.relativize(file.toPath)
    val targetFileLogs = git.log().add(revisionId).addPath(targetFile.toString).call()
    targetFileLogs.asScala.toList

  def commitTimeInstant(revCommit: RevCommit): OffsetDateTime =
    OffsetDateTime.ofInstant(Instant.ofEpochSecond(revCommit.getCommitTime), ZoneId.systemDefault())

  def getGitFileMetaData(git: Git, filePath: Path): Option[GitMetaData] =
    val targetFileLogs = getFileLog(git, filePath.toFile)
    val changesCount   = targetFileLogs.size
    for {
      createdOn   <- targetFileLogs.lastOption.map(commitTimeInstant)
      lastUpdated <- targetFileLogs.headOption.map(commitTimeInstant)
    } yield {
      GitMetaData(changesCount = changesCount, createdOn = createdOn, lastUpdated = lastUpdated)
    }

  def getGitFileMetaData(filePath: Path): Option[GitMetaData] =
    for {
      homeFile <- Properties.envOrNone("HOME").map(new File(_)).filter(_.exists())
      fromFile <- Option(filePath).map(_.toFile).filter(_.exists())
      repo     <- getLocalRepositoryFromFile(fromFile, Some(homeFile))
      git       = Git(repo) // TODO add close() call
      metadata <- getGitFileMetaData(git, filePath)
    } yield metadata

}
