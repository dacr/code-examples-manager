package fr.janalyse.cem

import zio.*
import zio.lmdb.LMDB
import zio.stream.*
import zio.stream.ZPipeline.{splitLines, utf8Decode}
import zio.process.*
import zio.json.*
import zio.ZIOAspect.*

import java.util.concurrent.TimeUnit
import java.time.OffsetDateTime
import java.util.UUID
import fr.janalyse.cem.model.*
import zio.nio.file.Path
import zio.Schedule.Decision

case class RunFailure(
  message: String
)
case class RunResults(
  command: List[String],
  exitCode: Int,
  output: String
)

object Execute {
  val timeoutDuration         = Duration(60, TimeUnit.SECONDS)
  val testStartDelay          = Duration(500, TimeUnit.MILLISECONDS)
  val defaultParallelismLevel = 8

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
      mayBeOutputLines <- stream.runCollect.disconnect
                            .timeout(timeoutDuration)
                            .mapError(err => RunFailure(s"Couldn't collect outputs\n${err.toString}"))
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
      results          <- makeCommandProcess(command, workingDir) @@ annotated("/run-command" -> command.mkString(" "))
    } yield results
  }

  def makeTestCommandProcess(example: CodeExample) = {
    for {
      exampleFilePath <- ZIO.fromOption(example.filepath).orElseFail(RunFailure("Example has no path for its content"))
      workingDir      <- ZIO.fromOption(exampleFilePath.parent).orElseFail(RunFailure("Example file path content has no parent directory"))
      command         <- ZIO.succeed(example.testWith.getOrElse(s"sleep ${timeoutDuration.getSeconds()}").trim.split("\\s+").toList)
      results         <- makeCommandProcess(command, workingDir) @@ annotated("/test-command" -> command.mkString(" "))
    } yield results
  }

  def runExample(example: CodeExample, runSessionDate: OffsetDateTime, runSessionUUID: UUID) = {
    val result =
      for {
        startTimestamp <- Clock.currentDateTime
        runEffect       = makeRunCommandProcess(example).disconnect
                            .timeout(timeoutDuration)
        testEffect      = makeTestCommandProcess(example)
                            .filterOrElseWith(result => result.exitCode == 0)(result => ZIO.fail(RunFailure(s"test code is failing + ${result.output}")))
                            .retry(
                              (Schedule.exponential(1.second) && Schedule.recurs(6))
                                .onDecision((state, out, decision) =>
                                  decision match {
                                    case Decision.Done               => ZIO.logError("No more retry attempt !")
                                    case Decision.Continue(interval) => ZIO.logWarning(s"Failed, will retry at ${interval.start}")
                                  }
                                )
                            )
                            .disconnect
                            .delay(testStartDelay)
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
        runStatus       = RunStatus(
                            example = example,
                            exitCodeOption = exitCodeOption,
                            // stdout = output,
                            stdout = output.take(1024), // truncate the output as some scripts may generate a lot of data !!!
                            startedTimestamp = startTimestamp,
                            duration = duration,
                            runSessionDate = runSessionDate,
                            runSessionUUID = runSessionUUID,
                            success = success,
                            timeout = timeout,
                            runState = runState
                          )
        _              <- upsertRunStatus(runStatus)
      } yield runStatus

    // ZIO.logAnnotate("/file", example.filename)(result)
    result
  }

  def runTestableExamples(runnableExamples: List[CodeExample], parallelism: Int) = {
    val execStrategy = ExecutionStrategy.ParallelN(parallelism)
    for {
      runSessionDate <- Clock.currentDateTime
      startEpoch     <- Clock.instant.map(_.toEpochMilli)
      runSessionUUID  = UUID.randomUUID()
      // runStatuses    <- ZIO.foreachExec(runnableExamples)(execStrategy)(example => runExample(example, runSessionDate, runSessionUUID))
      runStatuses    <- ZIO.foreachExec(runnableExamples)(execStrategy) { example =>
                          ZIO.logSpan("/run") {
                            runExample(example, runSessionDate, runSessionUUID)
                              @@ annotated("/uuid" -> example.uuid.toString, "/file" -> example.filename)
                          }
                        }
      successes       = runStatuses.filter(_.success)
      failures        = runStatuses.filterNot(_.success)
      endEpoch       <- Clock.instant.map(_.toEpochMilli)
      durationSeconds = (endEpoch - startEpoch) / 1000
      _              <- reportInLog(runStatuses, durationSeconds)
    } yield runStatuses
  }

  def reportInLog(results: List[RunStatus], durationSeconds: Long) = {
    val successes = results.filter(_.success)
    val failures  = results.filterNot(_.success)
    for {
      _ <- if (failures.size > 0)
             ZIO.logError(
               failures // runStatuses
                 .sortBy(s => (s.success, s.example.filepath.map(_.toString)))
                 .map(state => s"""${if (state.success) "OK" else "KO"} : ${state.example.filepath.get} : ${state.example.summary.getOrElse("")}""")
                 .mkString("\n", "\n", "")
             )
           else ZIO.log("ALL examples executed with success :)")
      _ <- ZIO.log(s"${successes.size} successes")
      _ <- ZIO.log(s"${failures.size} failures")
      _ <- ZIO.log(s"${results.size} runnable examples (with scala-cli) in ${durationSeconds}s")

    } yield ()
  }

  def upsertRunStatus(result: RunStatus) = {
    val collectionName = "run-statuses"
    val key            = result.example.uuid.toString
    val examplePath    = result.example.filepath.getOrElse(Path(result.example.filename))
    for {
      collection <- LMDB
                      .collectionGet[RunStatus](collectionName)
                      .orElse(LMDB.collectionCreate[RunStatus](collectionName))
                      .mapError(th => ExampleStorageIssue(examplePath, s"Storage issue with collection $collectionName"))
      _          <- collection
                      .upsertOverwrite(key, result)
                      .mapError(th => ExampleStorageIssue(examplePath, s"Couldn't upsert anything in collection $collectionName"))
    } yield ()
  }

  def executeEffect(keywords: Set[String] = Set.empty): ZIO[FileSystemService & LMDB, Throwable | ExampleIssue, List[RunStatus]] = ZIO.logSpan("/runs") {
    for {
      _                                            <- ZIO.log("Searching examples...")
      examples                                     <- Synchronize.examplesCollect
      filteredExamples                              = examples.filter(example => keywords.isEmpty || example.keywords.intersect(keywords) == keywords)
      testableExamples                              = filteredExamples.filter(_.runWith.isDefined).filter(_.isTestable)
      (exclusiveRunnableExamples, runnableExamples) = testableExamples.partition(_.isExclusive)
      _                                            <- ZIO.log(s"Found ${examples.size} examples with ${testableExamples.size} marked as testable")
      startEpoch                                   <- Clock.instant.map(_.toEpochMilli)
      exclusiveRunnableResultsFiber                <- runTestableExamples(exclusiveRunnableExamples, 1).fork
      runnableResultsFiber                         <- runTestableExamples(runnableExamples, defaultParallelismLevel).fork
      exclusiveRunnableResults                     <- exclusiveRunnableResultsFiber.join
      runnableResults                              <- runnableResultsFiber.join
      endEpoch                                     <- Clock.instant.map(_.toEpochMilli)
      durationSeconds                               = (endEpoch - startEpoch) / 1000
      results                                       = exclusiveRunnableResults ++ runnableResults
      // _                                            <- ZIO.foreach(results)(result => upsertRunStatus(result))
      _                                            <- reportInLog(results, durationSeconds)
    } yield results
  }

}
