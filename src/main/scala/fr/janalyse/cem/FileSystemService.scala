package fr.janalyse.cem

import zio.*
import zio.nio.charset.Charset
import zio.nio.file.*
import zio.stream.*

import java.io.{File, IOException}
import scala.util.matching.Regex

trait FileSystemService {
  def readFileContent(inputPath: Path): Task[String]
  def searchFiles(searchRoot: Path, searchOnlyRegex: Option[Regex], ignoreMaskRegex: Option[Regex]): Task[List[Path]]
}

object FileSystemService {
  def readFileContent(inputPath: Path): ZIO[FileSystemService, Throwable, String] =
    ZIO.serviceWithZIO(_.readFileContent(inputPath))
  def searchFiles(searchRoot: Path, searchOnlyRegex: Option[Regex], ignoreMaskRegex: Option[Regex]): ZIO[FileSystemService, Throwable, List[Path]] =
    ZIO.serviceWithZIO(_.searchFiles(searchRoot, searchOnlyRegex, ignoreMaskRegex))
}
