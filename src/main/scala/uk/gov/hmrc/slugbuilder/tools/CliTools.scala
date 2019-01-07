/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.slugbuilder.tools

import java.nio.file.Path
import uk.gov.hmrc.slugbuilder.ProgressReporter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.sys.process.{Process, ProcessLogger}
import scala.util.{Failure, Success}

class CliTools(progressReporter: ProgressReporter) {

  def run(cmd: Array[String], inDir: Option[Path] = None): Either[String, Unit] = {
    val cmdF = Future {
      val pb = inDir.fold(Process(cmd)) { path =>
        Process(cmd, cwd = path.toFile)
      }

      val logger   = ProcessLogger(s => progressReporter.printSuccess(s), e => progressReporter.printError(e))
      val exitCode = pb.!(logger)

      if (exitCode != 0) Left(s"got exit code $exitCode from command '${cmd.mkString(" ")}'") else Right(())
    }

    Await.ready(cmdF, 2 minutes)

    cmdF.value.get match {
      case Success(v) => v
      case Failure(e) => Left(e.getMessage)
    }
  }
}
