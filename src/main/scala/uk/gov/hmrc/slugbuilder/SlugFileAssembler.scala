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

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermission._
import java.nio.file.{Files, OpenOption, Path, Paths}
import java.util.stream.{Stream => JavaStream}

import cats.data.EitherT
import cats.implicits._
import uk.gov.hmrc.slugbuilder.functions.ArtifactFileName
import uk.gov.hmrc.slugbuilder.tools.CommandExecutor._
import uk.gov.hmrc.slugbuilder.tools.TarArchiver

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions

class SlugFileAssembler(
  archiver: TarArchiver,
  startDockerScriptCreator: StartDockerScriptCreator,
  create: Path => Unit = path => path.toFile.mkdir(),
  setPermissions: (Path, Set[PosixFilePermission]) => Unit = (file, permissions) =>
    Files.setPosixFilePermissions(file, permissions),
  createFile: (Path, String, Charset, OpenOption) => Unit = (file, content, charset, openOption) =>
    Files.write(file, Seq(content), charset, openOption),
  listFiles: Path => JavaStream[Path] = path => Files.list(path)) {

  import startDockerScriptCreator._

  private val startDockerPermissions =
    Set(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ)

  def assemble(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): EitherT[Future, String, String] = {
    val artifact        = Paths.get(ArtifactFileName(repositoryName, releaseVersion))
    val slugDirectory   = Paths.get("slug")
    val startDockerFile = slugDirectory resolve Paths.get("start-docker.sh")
    val procFile        = slugDirectory resolve Paths.get("Procfile")
    val slugTarFile     = Paths.get(s"$repositoryName-$releaseVersion.tar")

    EitherT
      .fromEither[Future] {
        for {
          _ <- perform(create(slugDirectory)) leftMap (exception =>
                s"Couldn't create slug directory at $slugDirectory. Cause: ${exception.getMessage}")
          _ <- perform(archiver.decompress(artifact, slugDirectory)) leftMap (exception =>
                s"Couldn't decompress artifact from $artifact. Cause: $exception")
          _ <- ensureStartDockerExists(slugDirectory, repositoryName)
          _ <- perform(setPermissions(startDockerFile, startDockerPermissions)) leftMap (exception =>
                s"Couldn't change permissions of the $startDockerFile. Cause: ${exception.getMessage}")
          _ <- perform(createFile(procFile, s"web: ./${startDockerFile.toFile.getName}", UTF_8, CREATE_NEW)) leftMap (
                exception => s"Couldn't create the $procFile. Cause: ${exception.getMessage}")
          _ <- perform(archiver.tar(slugTarFile, listFiles(slugDirectory))) leftMap (exception =>
                s"Couldn't create the $slugTarFile. Cause: $exception")
        } yield ()
      }
      .map(_ => s"$slugTarFile slug file assembled")
  }
}
