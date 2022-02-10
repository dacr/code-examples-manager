package fr.janalyse.cem

import fr.janalyse.cem.model.CodeExample
import zio.*
import zio.nio.charset.Charset
import zio.nio.file.*
import zio.stream.*

import java.io.{File, IOException}
import java.nio.file.attribute.BasicFileAttributes
import scala.util.matching.Regex

trait FileSystemService:
  def readFileContent(inputPath: Path): Task[String]
  def readFileLines(inputPath: Path, maxLines: Option[Int] = None): Task[Chunk[String]]
  def searchFiles(searchRoot: Path, searchOnlyRegex: Option[Regex], ignoreMaskRegex: Option[Regex]): Task[List[Path]]

object FileSystemService:
  def readFileContent(inputPath: Path): ZIO[FileSystemService, Throwable, String] =
    ZIO.serviceWithZIO(_.readFileContent(inputPath))

  def readFileLines(inputPath: Path, maxLines: Option[Int] = None): ZIO[FileSystemService, Throwable, Chunk[String]] =
    ZIO.serviceWithZIO(_.readFileLines(inputPath, maxLines))

  def searchFiles(searchRoot: Path, searchOnlyRegex: Option[Regex], ignoreMaskRegex: Option[Regex]): ZIO[FileSystemService, Throwable, List[Path]] =
    ZIO.serviceWithZIO(_.searchFiles(searchRoot, searchOnlyRegex, ignoreMaskRegex))

  def live: URLayer[ApplicationConfig, FileSystemService] = ZLayer(
    for applicationConfig <- ZIO.service[ApplicationConfig]
    yield FileSystemServiceImpl(applicationConfig)
  )

class FileSystemServiceImpl(applicationConfig: ApplicationConfig) extends FileSystemService:

  override def readFileContent(inputPath: Path): Task[String] =
    for
      charset <- IO.attempt(Charset.forName("UTF-8")) // TODO move to application config
      content <- Files.readAllBytes(inputPath)
    yield String(content.toArray, charset.name)

  override def readFileLines(inputPath: Path, maxLines: Option[Int]): Task[Chunk[String]] =
    for
      charset       <- IO.attempt(Charset.forName("UTF-8")) // TODO move to application config
      stream         = Files.lines(inputPath, charset)
      selectedStream = maxLines.map(n => stream.take(n)).getOrElse(stream)
      lines         <- selectedStream.runCollect
    yield lines

  def searchPredicate(searchOnlyRegex: Option[Regex], ignoreMaskRegex: Option[Regex])(path: Path, attrs: BasicFileAttributes): Boolean =
    attrs.isRegularFile &&
      (ignoreMaskRegex.isEmpty || ignoreMaskRegex.get.findFirstIn(path.toString).isEmpty) &&
      (searchOnlyRegex.isEmpty || searchOnlyRegex.get.findFirstIn(path.toString).isDefined)

  override def searchFiles(searchRoot: Path, searchOnlyRegex: Option[Regex], ignoreRegex: Option[Regex]): Task[List[Path]] =
    for {
      searchPath <- IO.attempt(searchRoot)
      stream      = Files.find(searchPath, 10)(searchPredicate(searchOnlyRegex, ignoreRegex))
      foundFiles <- stream.runCollect
    } yield foundFiles.toList
