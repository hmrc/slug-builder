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
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{Files, OpenOption, Path}
import cats.implicits._
import uk.gov.hmrc.slugbuilder.tools._
import scala.collection.JavaConversions._
import scala.language.implicitConversions
//
//class SlugFileAssembler(
//  progressReporter: ProgressReporter,
//  archiver: TarArchiver,
//  startDockerScriptCreator: StartDockerScriptCreator,
//  assemblerSteps: AssemblerSteps) {
//
//  import assemblerSteps._
//  import startDockerScriptCreator._
//
//  private val startDockerPermissions =
//    Set(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ)
//
//  def assemble(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): Either[String, String] = {
//    val artifact        = Paths.get(ArtifactFileName(repositoryName, releaseVersion))
//    val slugDirectory   = Paths.get("slug")
//    val startDockerFile = slugDirectory resolve Paths.get("start-docker.sh")
//    val procFile        = slugDirectory resolve Paths.get("Procfile")
//    val slugTarFile     = Paths.get(s"$repositoryName-$releaseVersion.tar")
//
//    for {
//      _ <- perform(createDir(slugDirectory)).leftMap(exception =>
//            s"Couldn't create slug directory at $slugDirectory. Cause: ${exception.getMessage}")
//      _ <- perform(archiver.decompress(artifact, slugDirectory))
//            .leftMap(exception => s"Couldn't decompress artifact from $artifact. Cause: $exception")
//      _ <- ensureStartDockerExists(slugDirectory, repositoryName) map (_ => "ensured start-docker exists")
//      _ <- perform(setPermissions(startDockerFile, startDockerPermissions)).leftMap(exception =>
//            s"Couldn't change permissions of the $startDockerFile. Cause: ${exception.getMessage}")
//      _ <- perform(createFile(procFile, s"web: ./${startDockerFile.toFile.getName}", UTF_8, CREATE_NEW))
//            .leftMap(exception => s"Couldn't create the $procFile. Cause: ${exception.getMessage}")
//      _ <- perform(createDir(slugDirectory.resolve(".jdk"))).leftMap(exception =>
//            s"Couldn't create .jdk directory at $slugDirectory/.jdk. Cause: ${exception.getMessage}")
//      _ <- downloadJdk().leftMap(message =>
//            s"Couldn't download the JDK from ${assemblerSteps.javaDownloadUri}. Cause: $message")
////      _ <- perform(archiver.decompress(Paths.get(s"$javaVendor-$javaVersion.tgz"), slugDirectory.resolve(".jdk")))
////            .leftMap(exception =>
////              s"Couldn't extract the JDK tar to $slugDirectory/.jdk. Cause: ${exception.getMessage}")
//    } yield s"$slugTarFile slug file assembled"
//  }
//}

class JdkFetcher(
  fileDownloader: FileDownloader,
  val javaDownloadUri: String,
  val javaVendor: String,
  val javaVersion: String) {

  def createDir(dir: Path): Unit =
    if (Files.exists(dir)) () else dir.toFile.mkdir()

  def setPermissions(file: Path, permissions: Set[PosixFilePermission]): Unit =
    Files.setPosixFilePermissions(file, permissions)

  def createFile(file: Path, content: String, charset: Charset, openOption: OpenOption): Unit =
    Files.write(file, Seq(content), charset, openOption)

  def destinationFileName = s"$javaVendor-$javaVersion.tgz"

  def download: Either[String, String] =
    fileDownloader
      .download(FileUrl(javaDownloadUri), DestinationFileName(destinationFileName))
      .bimap(
        downloadError => s"JDK couldn't be downloaded from $javaDownloadUri. Cause: $downloadError",
        _ => s"Successfully downloaded JDK from $javaDownloadUri"
      )

}
