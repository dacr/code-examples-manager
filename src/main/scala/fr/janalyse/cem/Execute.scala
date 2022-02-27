package fr.janalyse.cem

import zio.*
import zio.stream.*
import zio.stream.ZPipeline.{splitLines, utf8Decode}
import zio.process.*
import zio.json.*

import java.util.concurrent.TimeUnit
import java.time.OffsetDateTime
import java.util.UUID
import fr.janalyse.cem.model.{CodeExample, RunStatus, ExampleIssue}
import zio.nio.file.Path

object Execute {

  def runExample(example: CodeExample, runSessionDate: OffsetDateTime, runSessionUUID: UUID): ZIO[Clock & Console, Throwable, RunStatus] = {
    val timeoutDuration = Duration(100, TimeUnit.SECONDS)
    val uuid            = example.uuid

    val result: ZIO[Clock & Console, Throwable, RunStatus] = for {
      exampleFilePath  <- ZIO.fromOption(example.filepath).orElseFail(Exception(s"Example $uuid has no path to its content"))
      absoluteFileName <- exampleFilePath.toAbsolutePath
      command          <- ZIO
                            .fromOption(
                              example.runWith
                                .map(_.replaceAll("[$]scriptFile", absoluteFileName.toString))
                                .map(_.replaceAll("[$]file", absoluteFileName.toString))
                                .map(_.split("\\s+").toList)
                            )
                            .orElseFail(Exception(s"Example ${example.uuid} as no run-with directive"))
      _                <- ZIO.log(s"Running $exampleFilePath")
      startTimestamp   <- Clock.currentDateTime
      executable       <- ZIO.fromOption(command.headOption).orElseFail(Exception(s"Example $uuid command is invalid : ${command}"))
      arguments         = command.drop(1)
      workingDir       <- ZIO.fromOption(exampleFilePath.parent).orElseFail(Exception(s"Example $uuid file path content has no parent"))
      process          <- Command(executable, arguments*)
                            .redirectErrorStream(true)
                            .workingDirectory(workingDir.toFile)
                            .run
      stream            = process.stdout.stream.via(utf8Decode >>> splitLines)
      outputFiber      <- stream.runCollect.fork
      exitCodeOption   <- process.exitCode.timeout(timeoutDuration)
      _                <- process.killTree.ignore
      outputLines      <- outputFiber.join.catchAll(err => ZIO.succeed(Chunk(err.toString)))
      outputText        = outputLines.mkString("\n")
      duration         <- Clock.instant.map(i => i.toEpochMilli - startTimestamp.toInstant.toEpochMilli)
      success           = exitCodeOption.exists(_.code == 0) || (example.shouldFail && exitCodeOption.exists(_.code > 0))
      timeout           = exitCodeOption.isEmpty
      runState          = if timeout then "timeout" else if success then "success" else "failure"
      // _              <- ZIO.log(s"Execution state is $runState running ${example.filepath} in ${workingDir} using ${command.mkString(" ")}")
    } yield RunStatus(
      example = example,
      exitCodeOption = exitCodeOption.map(_.code),
      Stdout = outputText,
      startedTimestamp = startTimestamp,
      duration = duration,
      runSessionDate = runSessionDate,
      runSessionUUID = runSessionUUID,
      success = success,
      timeout = timeout,
      runState = runState
    )

    result
      // .tap(status => Console.printLine(s"${example.filename} result state ${status.runState} "))
      .tapError(err => ZIO.logError(s"Example $uuid (${example.filename}) has failed with $err"))
  }

  def runTestableExamples(examples: List[CodeExample]): ZIO[Console & Clock, Throwable, List[RunStatus]] = {
    val runnableExamples = examples
      .filter(_.runWith.isDefined)
      .filter(_.isTestable)
    val execStrategy     = ExecutionStrategy.ParallelN(8) // TODO Take the number of CPU core
    for {
      runSessionDate <- Clock.currentDateTime
      runSessionUUID  = UUID.randomUUID()
      runStatuses    <- ZIO.foreachExec(runnableExamples)(execStrategy)(example => runExample(example, runSessionDate, runSessionUUID))
      successes       = runStatuses.filter(_.success)
      failures        = runStatuses.filterNot(_.success)
      _              <- ZIO.logError(
                          failures // runStatuses
                            .sortBy(s => (s.success, s.example.filepath.map(_.toString)))
                            .map(state => s"""${if (state.success) "OK" else "KO"} : ${state.example.filepath.get} : ${state.example.summary.getOrElse("")}""")
                            .mkString("\n")
                        )
      _              <- ZIO.log("%d runnable examples (with scala-cli)".formatted(runStatuses.size))
      _              <- ZIO.log("%d successes".formatted(successes.size))
      _              <- ZIO.log("%d failures".formatted(failures.size))
    } yield runStatuses
  }

  def executeEffect(keywords: Set[String] = Set.empty): ZIO[Console & Clock & ApplicationConfig & FileSystemService, Throwable | ExampleIssue, List[RunStatus]] = {
    for {
      _               <- ZIO.log("Running examples...")
      examples        <- Synchronize.examplesCollect
      filteredExamples = examples.filter(example => keywords.isEmpty || example.keywords.intersect(keywords) == keywords)
      results         <- runTestableExamples(filteredExamples)
    } yield results
  }

}
