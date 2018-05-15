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
import java.nio.file.{Path, Paths}

import cats.data.EitherT
import cats.implicits._
import org.rauschig.jarchivelib.Archiver
import uk.gov.hmrc.slugbuilder.tools.CommandExecutor._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions

class SlugFileAssembler(
  archiver: Archiver,
  startDockerScriptCreator: StartDockerScriptCreator,
  create: Path => Unit = path => path.toFile.mkdir()) {

  import startDockerScriptCreator._

  def assemble(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): EitherT[Future, String, String] = {
    val artifact      = Paths.get(s"$repositoryName-$releaseVersion.tgz")
    val slugDirectory = Paths.get("slug")

    EitherT
      .fromEither[Future] {
        for {
          _ <- perform(create(slugDirectory)) leftMap (exception =>
                s"Couldn't create slug directory at $slugDirectory. Cause: ${exception.getMessage}")
          _ <- perform(archiver.extract(artifact, slugDirectory)) leftMap (exception =>
                s"Couldn't decompress artifact from $artifact. Cause: ${exception.getMessage}")
          _ <- ensureStartDockerExists(slugDirectory, repositoryName)
        } yield ()
      }
      .map(_ => s"$artifact slug file assembled")
  }

  private implicit def pathToFile(path: Path): File = path.toFile
}
