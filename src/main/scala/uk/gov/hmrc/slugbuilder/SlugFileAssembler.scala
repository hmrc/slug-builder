/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.slugbuilder

import java.io.File
import java.nio.file.Paths

import cats.data.EitherT
import cats.implicits._
import org.rauschig.jarchivelib.Archiver

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class SlugFileAssembler(archiver: Archiver, createDir: File => Unit = file => file.mkdir()) {

  def assemble(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): EitherT[Future, String, String] = {
    val artifact        = Paths.get(s"$repositoryName-$releaseVersion.tgz").toFile
    val outputDirectory = Paths.get("slug").toFile

    EitherT
      .fromEither[Future] {
        for {
          _ <- perform(createDir(outputDirectory)) leftMap (exception =>
                s"Couldn't create slug directory at ${outputDirectory.getName}. Cause: ${exception.getMessage}")
          _ <- perform(archiver.extract(artifact, outputDirectory)) leftMap (exception =>
                s"Couldn't decompress artifact from ${artifact.getName}. Cause: ${exception.getMessage}")
        } yield ()
      }
      .map(_ => s"${artifact.getName} slug file assembled")
  }

  private def perform(operation: => Unit): Either[Exception, Unit] =
    Either
      .fromTry {
        Try(operation)
      }
      .leftMap {
        case exception: Exception => exception
      }
}
