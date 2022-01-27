/*
 * Copyright 2022 HM Revenue & Customs
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

import cats.implicits._
import uk.gov.hmrc.slugbuilder.connectors.{ArtifactoryConnector, GithubConnector}
import uk.gov.hmrc.slugbuilder.tools.{CommandExecutor, FileUtils, TarArchiver}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Path, Paths}
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.attribute.PosixFilePermission._

class SlugBuilder(
  progressReporter        : ProgressReporter,
  artifactoryConnector    : ArtifactoryConnector,
  githubConnector         : GithubConnector,
  archiver                : TarArchiver,
  startDockerScriptCreator: StartDockerScriptCreator,
  fileUtils               : FileUtils
) {
  import CommandExecutor.perform
  import fileUtils.{createDir, createFile, setPermissions}
  import progressReporter.{printError, printSuccess}

  private val startDockerPermissions =
    Set(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ)

  def create(
    repositoryName     : RepositoryName,
    releaseVersion     : ReleaseVersion,
    slugRuntimeJavaOpts: Option[SlugRuntimeJavaOpts],
    buildProperties    : Map[String, String],
    includeFiles       : Option[String],
    artefactLocation   : Option[String],
    publish            : Boolean
  ): Either[Unit, Unit] = {

    val artefact            = ArtefactFileName(repositoryName, releaseVersion)
    val jdk                 = Paths.get("jdk.tgz")
    val workspaceDirectory  = Paths.get(".")
    val slugDirectory       = Paths.get("slug")
    val startDockerFile     = slugDirectory.resolve(Paths.get("start-docker.sh"))
    val procFile            = slugDirectory.resolve(Paths.get("Procfile"))
    val buildPropertiesFile = slugDirectory.resolve(Paths.get("build.properties"))
    val slugTgzFile         = Paths.get(artifactoryConnector.slugArtefactFileName(repositoryName, releaseVersion))
    val jdkDirectory        = slugDirectory.resolve(".jdk")
    val profileD            = slugDirectory.resolve(".profile.d")
    val javaSh              = profileD.resolve("java.sh")

    for {
      _ <- if (publish)
             artifactoryConnector.verifySlugNotCreatedYet(repositoryName, releaseVersion)
               .map(printSuccess)
           else Right(())
      _ <- (artefactLocation match {
              case Some(location) => artifactoryConnector.copyLocalArtefact(repositoryName, releaseVersion, location, artefact)
              case None           => artifactoryConnector.downloadArtefact(repositoryName, releaseVersion, artefact)
           }).map(printSuccess)
      _ <- githubConnector.downloadAppConfigBase(repositoryName)
             .map(printSuccess)
      _ <- perform(createDir(slugDirectory))
             .leftMap(exception => s"Couldn't create slug directory at $slugDirectory. Cause: ${exception.getMessage}")
      _ <- archiver.decompress(Paths.get(artefact.toString), slugDirectory)
             .map(printSuccess)
      _ <- startDockerScriptCreator.ensureStartDockerExists(
             workspaceDirectory,
             slugDirectory,
             repositoryName,
             slugRuntimeJavaOpts
           ).map(printSuccess)
      _ <- perform(setPermissions(startDockerFile, startDockerPermissions))
             .leftMap(exception => s"Couldn't change permissions of the $startDockerFile. Cause: ${exception.getMessage}")
      _ <- perform(createFile(procFile, s"web: ./${startDockerFile.toFile.getName}", UTF_8, CREATE_NEW))
            .leftMap(exception => s"Couldn't create the $procFile. Cause: ${exception.getMessage}")
      _ <- perform(
             createFile(buildPropertiesFile, mapToString(removeSensitiveProperties(buildProperties)), UTF_8, CREATE_NEW)
           ).leftMap(exception => s"Couldn't create the $buildPropertiesFile. Cause: ${exception.getMessage}")
      _ <- perform(copyFiles(includeFiles, slugDirectory))
             .leftMap(exception => s"Couldn't copy include files. Cause: ${exception.getMessage}")
             .map(printSuccess)
      _ <- perform(createDir(jdkDirectory))
             .leftMap(exception => s"Couldn't create .jdk directory at $jdkDirectory. Cause: ${exception.getMessage}")
      _ <- artifactoryConnector.downloadJdk(jdk.toString)
             .map(printSuccess)
      _ <- archiver.decompress(jdk, jdkDirectory)
             .map(printSuccess)
      _ <- perform(createDir(profileD))
             .leftMap(exception => s"Couldn't create $profileD directory. Cause: ${exception.getMessage}")
      _ <- perform(createJavaSh(javaSh))
             .leftMap(exception => s"Couldn't create the $javaSh. Cause: ${exception.getMessage}")
             .map(_ => printSuccess(s"Successfully created .profile.d/java.sh"))
      _ <- archiver.compress(slugTgzFile, slugDirectory)
             .map(printSuccess)
      _ <- if (publish)
             artifactoryConnector.publish(repositoryName, releaseVersion)
               .map(printSuccess)
           else Right(())
    } yield ()
  }.leftMap(printError)

  private def mapToString(buildProperties: Map[String, String]): String =
    buildProperties
      .map { case (k, v) => s"$k=$v" }
      .mkString("\n")

  private def removeSensitiveProperties(properties: Map[String, String]): Map[String, String] = {
    val sensitiveKeys = Seq("pass", "token", "user", "key", "secret", "cookie")
    properties.view.filterKeys { key =>
      !sensitiveKeys.exists(key.toLowerCase.contains(_))
    }.toMap
  }

  private def createJavaSh(javaSh: Path): Unit =
    createFile(
      javaSh,
      """PATH=$HOME/.jdk/bin:$PATH
        |JAVA_HOME=$HOME/.jdk/""".stripMargin,
      UTF_8,
      CREATE_NEW
    )

  private def copyFiles(optFileCsv: Option[String], targetDirectory: Path): String =
    optFileCsv.
      fold("No files to copy"){ fileCsv =>
        val files = fileCsv.split(",").map(Paths.get(_))
        files.map(fileUtils.copyFile(_, targetDirectory))
        s"Copied ${files.map(_.toString).mkString(", ")} into $targetDirectory"
      }
}
