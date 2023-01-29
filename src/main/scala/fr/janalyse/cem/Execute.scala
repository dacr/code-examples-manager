package fr.janalyse.cem

import zio.*
import zio.stream.*
import zio.stream.ZPipeline.{splitLines, utf8Decode}
import zio.process.*
import zio.json.*
import zio.ZIOAspect.*

import java.util.concurrent.TimeUnit
import java.time.OffsetDateTime
import java.util.UUID
import fr.janalyse.cem.model.{CodeExample, RunStatus, ExampleIssue}
import zio.nio.file.Path

case class RunFailure(
  message: String
)
case class RunResults(
  command: List[String],
  exitCode: Int,
  output: String
)

object Execute {
  val timeoutDuration = Duration(100, TimeUnit.SECONDS)
  val testStartDelay  = Duration(500, TimeUnit.MILLISECONDS)

  def makeCommandProcess(command: List[String], workingDir: Path) = {
    val results = for {
      executable       <- ZIO.from(command.headOption).orElseFail(RunFailure("Example command is invalid"))
      arguments         = command.drop(1)
      process          <- ZIO.acquireRelease(
                            Command(executable, arguments*)
                              .redirectErrorStream(true)
                              .workingDirectory(workingDir.toFile)
                              .run
                              .mapError(err => RunFailure(s"Command error ${err.toString}"))
                          )(process => process.killTreeForcibly.tapError(err => ZIO.logError(err.toString)).ignore)
      stream            = process.stdout.stream.via(utf8Decode >>> splitLines)
      mayBeOutputLines <- stream.runCollect.disconnect.timeout(timeoutDuration).mapError(err => RunFailure(s"Couldn't collect outputs\n${err.toString}"))
      outputText        = mayBeOutputLines.map(chunks => chunks.mkString("\n")).getOrElse("")
      exitCode         <- process.exitCode.mapError(err => RunFailure(outputText + "\n" + err.toString))
    } yield RunResults(command, exitCode.code, outputText)

    ZIO.scoped(results)
  }

  def makeRunCommandProcess(example: CodeExample) = {
    for {
      exampleFilePath  <- ZIO.fromOption(example.filepath).orElseFail(RunFailure("Example has no path for its content"))
      workingDir       <- ZIO.fromOption(exampleFilePath.parent).orElseFail(RunFailure("Example file path content has no parent directory"))
      absoluteFileName <- exampleFilePath.toAbsolutePath.orElseFail(RunFailure("Example absolute file path error"))
      command          <- ZIO
                            .from(
                              example.runWith
                                .map(_.replaceAll("[$]scriptFile", absoluteFileName.toString))
                                .map(_.replaceAll("[$]file", absoluteFileName.toString))
                                .map(_.split("\\s+").toList)
                            )
                            .orElseFail(RunFailure(s"Example ${example.uuid} as no run-with directive"))
      results          <- makeCommandProcess(command, workingDir) @@ annotated("example-run-command" -> command.mkString(" "))
    } yield results
  }

  def makeTestCommandProcess(example: CodeExample) = {
    for {
      exampleFilePath <- ZIO.fromOption(example.filepath).orElseFail(RunFailure("Example has no path for its content"))
      workingDir      <- ZIO.fromOption(exampleFilePath.parent).orElseFail(RunFailure("Example file path content has no parent directory"))
      command         <- ZIO.succeed(example.testWith.getOrElse(s"sleep ${timeoutDuration.getSeconds()}").trim.split("\\s+").toList)
      results         <- makeCommandProcess(command, workingDir) @@ annotated("example-test-command" -> command.mkString(" "))
    } yield results
  }

  def runExample(example: CodeExample, runSessionDate: OffsetDateTime, runSessionUUID: UUID) = {
    val result =
      for {
        startTimestamp <- Clock.currentDateTime
        runEffect       = makeRunCommandProcess(example).disconnect
                            .timeout(timeoutDuration)
        testEffect      = makeTestCommandProcess(example)
                            .filterOrFail(result => result.exitCode == 0)(RunFailure("test code is failing"))
                            .retry(Schedule.exponential(100.millis, 2).jittered && Schedule.recurs(5))
                            .delay(testStartDelay)
                            .disconnect
                            .timeout(timeoutDuration)
        results        <- runEffect.raceFirst(testEffect).either
        duration       <- Clock.instant.map(i => i.toEpochMilli - startTimestamp.toInstant.toEpochMilli)
        timeout         = results.exists(_.isEmpty)
        output          = results.toOption.flatten.map(_.output).getOrElse("")
        exitCodeOption  = results.toOption.flatten.map(_.exitCode)
        success         = exitCodeOption.exists(_ == 0) || (example.shouldFail && exitCodeOption.exists(_ != 0))
        runState        = if timeout then "timeout" else if success then "success" else "failure"
        _              <- if (results.isLeft) ZIO.logError(s"""Couldn't execute either run or test part\n${results.swap.toOption.getOrElse("")}""") else ZIO.succeed(())
        _              <- if (!success) ZIO.logWarning(s"example run $runState\nFailed cause:\n$output") else ZIO.log("example run success")
      } yield RunStatus(
        example = example,
        exitCodeOption = exitCodeOption,
        stdout = output,
        startedTimestamp = startTimestamp,
        duration = duration,
        runSessionDate = runSessionDate,
        runSessionUUID = runSessionUUID,
        success = success,
        timeout = timeout,
        runState = runState
      )

    ZIO.logAnnotate("file",example.filename)(result)
  }

  def runTestableExamples(examples: List[CodeExample]) = {
    val runnableExamples = examples
      .filter(_.runWith.isDefined)
      .filter(_.isTestable)
    val execStrategy     = ExecutionStrategy.ParallelN(8) // TODO Take the number of CPU core
    for {
      runSessionDate <- Clock.currentDateTime
      startEpoch     <- Clock.instant.map(_.toEpochMilli)
      runSessionUUID  = UUID.randomUUID()
      // runStatuses    <- ZIO.foreachExec(runnableExamples)(execStrategy)(example => runExample(example, runSessionDate, runSessionUUID))
      runStatuses    <- ZIO.foreach(runnableExamples) { example =>
                          runExample(example, runSessionDate, runSessionUUID) @@ annotated("example-uuid" -> example.uuid.toString, "example-filename" -> example.filename)
                        }
      successes       = runStatuses.filter(_.success)
      failures        = runStatuses.filterNot(_.success)
      _              <- if (failures.size > 0)
                          ZIO.logError(
                            failures // runStatuses
                              .sortBy(s => (s.success, s.example.filepath.map(_.toString)))
                              .map(state => s"""${if (state.success) "OK" else "KO"} : ${state.example.filepath.get} : ${state.example.summary.getOrElse("")}""")
                              .mkString("\n", "\n", "")
                          )
                        else ZIO.log("ALL example executed with success :)")
      endEpoch       <- Clock.instant.map(_.toEpochMilli)
      durationSeconds = (endEpoch - startEpoch) / 1000
      _              <- ZIO.log(s"${runStatuses.size} runnable examples (with scala-cli) in ${durationSeconds}s")
      _              <- ZIO.log(s"${successes.size} successes")
      _              <- ZIO.log(s"${failures.size} failures")
    } yield runStatuses
  }

  def executeEffect(keywords: Set[String] = Set.empty): ZIO[ApplicationConfig & FileSystemService, Throwable | ExampleIssue, List[RunStatus]] = {
    for {
      _               <- ZIO.log("Searching examples...")
      examples        <- Synchronize.examplesCollect
      _               <- ZIO.log("Running selected examples...")
      filteredExamples = examples.filter(example => keywords.isEmpty || example.keywords.intersect(keywords) == keywords)
      results         <- runTestableExamples(filteredExamples)
    } yield results
  }

}
