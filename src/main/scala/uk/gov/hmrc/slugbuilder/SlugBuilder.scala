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

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Paths
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.attribute.PosixFilePermission._
import cats.implicits._
import uk.gov.hmrc.slugbuilder.functions.ArtifactFileName
import uk.gov.hmrc.slugbuilder.tools.CommandExecutor.perform
import uk.gov.hmrc.slugbuilder.tools.{CLITools, FileUtils, TarArchiver}

class SlugBuilder(
  progressReporter: ProgressReporter,
  slugChecker: SlugChecker,
  artifactFetcher: ArtifactFetcher,
  appConfigBaseFetcher: AppConfigBaseFetcher,
  jdkFetcher: JdkFetcher,
  archiver: TarArchiver,
  startDockerScriptCreator: StartDockerScriptCreator,
  fileUtils: FileUtils) {

  import progressReporter._
  import slugChecker._
  import fileUtils._

  private val startDockerPermissions =
    Set(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ)

  def create(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): Either[Unit, Unit] = {

    val artifact        = Paths.get(ArtifactFileName(repositoryName, releaseVersion))
    val slugDirectory   = Paths.get("slug")
    val startDockerFile = slugDirectory resolve Paths.get("start-docker.sh")
    val procFile        = slugDirectory resolve Paths.get("Procfile")
    val slugTarFile     = Paths.get(s"$repositoryName-$releaseVersion.tar")

    for {
      _ <- verifySlugNotCreatedYet(repositoryName, releaseVersion) map printSuccess
      _ <- artifactFetcher.download(repositoryName, releaseVersion) map printSuccess
      _ <- appConfigBaseFetcher.download(repositoryName) map printSuccess
      _ <- perform(createDir(slugDirectory)).leftMap(exception =>
            s"Couldn't create slug directory at $slugDirectory. Cause: ${exception.getMessage}")
      _ <- perform(archiver.decompress(artifact, slugDirectory))
            .leftMap(exception => s"Couldn't decompress artifact from $artifact. Cause: $exception")
      _ <- startDockerScriptCreator.ensureStartDockerExists(slugDirectory, repositoryName) map (_ =>
            "ensured start-docker exists")
      _ <- perform(setPermissions(startDockerFile, startDockerPermissions)).leftMap(exception =>
            s"Couldn't change permissions of the $startDockerFile. Cause: ${exception.getMessage}")
      _ <- perform(createFile(procFile, s"web: ./${startDockerFile.toFile.getName}", UTF_8, CREATE_NEW))
            .leftMap(exception => s"Couldn't create the $procFile. Cause: ${exception.getMessage}")
      _ <- perform(createDir(slugDirectory.resolve(".jdk"))).leftMap(exception =>
            s"Couldn't create .jdk directory at $slugDirectory/.jdk. Cause: ${exception.getMessage}")
      _ <- jdkFetcher.download
            .leftMap(message => s"Couldn't download the JDK from ${jdkFetcher.javaDownloadUri}. Cause: $message")
    } yield ()
  }.leftMap(printError)
}
