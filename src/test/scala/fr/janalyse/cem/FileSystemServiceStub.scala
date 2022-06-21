package fr.janalyse.cem

import zio.*
import zio.nio.file.Path

import scala.util.matching.Regex

class FileSystemServiceStub(contents: Map[Path, String] = Map.empty, files: Map[Path, List[Path]] = Map.empty) extends FileSystemService:

  override def searchFiles(searchRoot: Path, searchOnlyRegex: Option[Regex], ignoreMaskRegex: Option[Regex]): Task[List[Path]] =
    ZIO.getOrFail(files.get(searchRoot))

  override def readFileLines(inputPath: Path, maxLines: Option[Int]): Task[List[String]] =
    for
      content      <- ZIO.getOrFail(contents.get(inputPath))
      lines         = content.split("\r?\n").toList
      selectedLines = maxLines.map(n => lines.take(n)).getOrElse(lines)
    yield lines

  override def readFileContent(inputPath: Path): Task[String] =
    ZIO.getOrFail(contents.get(inputPath))

object FileSystemServiceStub:
  def stubWithContents(contents: Map[Path, String]): TaskLayer[FileSystemService] = ZLayer.succeed(
    FileSystemServiceStub(contents)
  )
