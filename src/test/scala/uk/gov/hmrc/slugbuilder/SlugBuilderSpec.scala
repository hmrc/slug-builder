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

package uk.gov.hmrc.slugbuilder

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Paths
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.attribute.PosixFilePermission._

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.{doThrow, verify, when}
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.slugbuilder.connectors.ArtifactoryConnector
import uk.gov.hmrc.slugbuilder.generators.Generators.Implicits._
import uk.gov.hmrc.slugbuilder.generators.Generators._
import uk.gov.hmrc.slugbuilder.tools.{FileUtils, TarArchiver}

class SlugBuilderSpec extends WordSpec with MockitoSugar {

  "create" should {

    "finish successfully if " +
      "slug for the given version of the microservice does not exist and " +
      "microservice artifact can be downloaded and " +
      "app-config-base can be downloaded and " +
      "a slug file is assembled" in new Setup {

      slugBuilder.create(repositoryName, releaseVersion, None) should be('right)

      progressReporter.logs should contain("Slug does not exist")
      progressReporter.logs should contain("Artifact downloaded")
      progressReporter.logs should contain("app-config-base downloaded")
      progressReporter.logs should contain("Successfully downloaded the JDK")
      progressReporter.logs should contain("Successfully decompressed jdk.tgz")
      progressReporter.logs should contain(s"Successfully compressed $slugTgzFile")
      progressReporter.logs should contain("Slug published successfully to https://artifactory/some-slug.tgz")
      progressReporter.logs should contain("Successfully created .profile.d/java.sh")

      verify(startDockerScriptCreator).ensureStartDockerExists(workspaceDirectory, slugDirectory, repositoryName, None)

      verify(fileUtils).createDir(slugDirectory)

      verify(fileUtils)
        .setPermissions(
          startDockerFile,
          Set(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ))

      verify(fileUtils).createFile(procFile, "web: ./start-docker.sh", UTF_8, CREATE_NEW)

      verify(fileUtils).createDir(slugDirectory.resolve(".jdk"))

      val profileD = slugDirectory.resolve(".profile.d")
      verify(fileUtils).createDir(profileD)

      val javaSh = profileD resolve "java.sh"
      verify(fileUtils)
        .createFile(javaSh, "PATH=$HOME/.jdk/bin:$PATH\nJAVA_HOME=$HOME/.jdk/", UTF_8, CREATE_NEW)
    }

    "finish successfully if " +
      "slug for the given version of the microservice does not exist and " +
      "microservice artifact can be downloaded and " +
      "app-config-base can be downloaded and " +
      "a slug file is assembled"
      "and custom JAVA_OPTS property has been provided" in new Setup {

      slugBuilder.create(repositoryName, releaseVersion, Some(SlugRuntimeJavaOpts("-Xmx256"))) should be('right)

      progressReporter.logs should contain("Slug does not exist")
      progressReporter.logs should contain("Artifact downloaded")
      progressReporter.logs should contain("app-config-base downloaded")
      progressReporter.logs should contain("Successfully downloaded the JDK")
      progressReporter.logs should contain("Successfully decompressed jdk.tgz")
      progressReporter.logs should contain(s"Successfully compressed $slugTgzFile")
      progressReporter.logs should contain("Slug published successfully to https://artifactory/some-slug.tgz")
      progressReporter.logs should contain("Successfully created .profile.d/java.sh")

      verify(startDockerScriptCreator).ensureStartDockerExists(workspaceDirectory, slugDirectory, repositoryName,
        Some(SlugRuntimeJavaOpts("-Xmx256")))

      verify(fileUtils).createDir(slugDirectory)

      verify(fileUtils)
        .setPermissions(
          startDockerFile,
          Set(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ))

      verify(fileUtils).createFile(procFile, "web: ./start-docker.sh", UTF_8, CREATE_NEW)

      verify(fileUtils).createDir(slugDirectory.resolve(".jdk"))

      val profileD = slugDirectory.resolve(".profile.d")
      verify(fileUtils).createDir(profileD)

      val javaSh = profileD resolve "java.sh"
      verify(fileUtils)
        .createFile(javaSh, "PATH=$HOME/.jdk/bin:$PATH\nJAVA_HOME=$HOME/.jdk/", UTF_8, CREATE_NEW)
    }


    "not create the slug if it already exists in the Webstore" in new Setup {
      when(artifactConnector.verifySlugNotCreatedYet(repositoryName, releaseVersion))
        .thenReturn(Left("Slug does exist"))

      slugBuilder.create(repositoryName, releaseVersion, None) should be('left)
      progressReporter.logs                              should contain("Slug does exist")
    }

    "not create the slug if there is no artifact in the Artifactory" in new Setup {

      when(
        artifactConnector
          .downloadArtifact(repositoryName, releaseVersion))
        .thenReturn(Left("Artifact does not exist"))

      slugBuilder.create(repositoryName, releaseVersion, None) should be('left)
      progressReporter.logs                              should contain("Artifact does not exist")
    }

    "not create the slug if there is app-config-base in the Webstore" in new Setup {

      when(artifactConnector.downloadAppConfigBase(repositoryName))
        .thenReturn(Left("app-config-base does not exist"))

      slugBuilder.create(repositoryName, releaseVersion, None) should be('left)
      progressReporter.logs                              should contain("app-config-base does not exist")
    }

    "return error message when slug directory cannot be created" in new Setup {

      val exception = new RuntimeException("exception message")
      doThrow(exception).when(fileUtils).createDir(slugDirectory)

      slugBuilder.create(repositoryName, releaseVersion, None) should be('left)
      progressReporter.logs should contain(
        s"Couldn't create slug directory at ${slugDirectory.toFile.getName}. Cause: ${exception.getMessage}")
    }

    "return error message when artifact cannot be extracted" in new Setup {
      when(tarArchiver.decompress(artifactFile, slugDirectory))
        .thenReturn(Left("Some error"))

      slugBuilder.create(repositoryName, releaseVersion, None) should be('left)
      progressReporter.logs                              should contain("Some error")
    }

    "return error when start-docker.sh creation return left" in new Setup {

      val errorMessage = "error message"
      when(startDockerScriptCreator.ensureStartDockerExists(workspaceDirectory, slugDirectory, repositoryName, None))
        .thenReturn(Left(errorMessage))

      slugBuilder.create(repositoryName, releaseVersion, None) should be('left)
      progressReporter.logs                              should contain("error message")
    }

    "return error when start-docker.sh permissions cannot be changed" in new Setup {
      val exception = new RuntimeException("exception message")
      doThrow(exception)
        .when(fileUtils)
        .setPermissions(
          startDockerFile,
          Set(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ))

      slugBuilder.create(repositoryName, releaseVersion, None) should be('left)
      progressReporter.logs should contain(
        s"Couldn't change permissions of the $startDockerFile. Cause: ${exception.getMessage}")
    }

    "return error if creating the Procfile fails" in new Setup {
      val exception = new RuntimeException("exception message")
      doThrow(exception).when(fileUtils).createFile(procFile, "web: ./start-docker.sh", UTF_8, CREATE_NEW)

      slugBuilder.create(repositoryName, releaseVersion, None) should be('left)
      progressReporter.logs                              should contain(s"Couldn't create the $procFile. Cause: ${exception.getMessage}")
    }

    "return error if creating the .jdk directory fails" in new Setup {
      val exception = new RuntimeException("exception message")
      doThrow(exception).when(fileUtils).createDir(slugDirectory.resolve(".jdk"))

      slugBuilder.create(repositoryName, releaseVersion, None) should be('left)
      progressReporter.logs should contain(
        s"Couldn't create .jdk directory at $slugDirectory/.jdk. Cause: ${exception.getMessage}")
    }

    "return error if downloading the JDK fails" in new Setup {
      when(artifactConnector.downloadJdk(jdkFileName))
        .thenReturn(Left("Error downloading JDK"))

      slugBuilder.create(repositoryName, releaseVersion, None) should be('left)
      progressReporter.logs                              should contain("Error downloading JDK")
    }

    "return error message when the JDK cannot be extracted" in new Setup {

      when(tarArchiver.decompress(Paths.get(jdkFileName), slugDirectory.resolve(".jdk")))
        .thenReturn(Left("Some error"))

      slugBuilder.create(repositoryName, releaseVersion, None) should be('left)
      progressReporter.logs                              should contain("Some error")
    }

    "return error message when the Slug cannot be compressed" in new Setup {
      when(tarArchiver.compress(slugTgzFile, slugDirectory))
        .thenReturn(Left("Some error"))

      slugBuilder.create(repositoryName, releaseVersion, None) should be('left)
      progressReporter.logs                              should contain(s"Some error")
    }

    "return error message when the Slug cannot be published" in new Setup {
      when(artifactConnector.publish(repositoryName, releaseVersion))
        .thenReturn(Left("Some error"))

      slugBuilder.create(repositoryName, releaseVersion, None) should be('left)
      progressReporter.logs                              should contain(s"Some error")
    }

    "not catch fatal Errors" in new Setup {
      val error = new OutOfMemoryError("error")
      doThrow(error).when(fileUtils).createDir(slugDirectory)

      intercept[Throwable](slugBuilder.create(repositoryName, releaseVersion, None)) shouldBe error
    }

  }

  private trait Setup {
    val repositoryName     = repositoryNameGen.generateOne
    val releaseVersion     = releaseVersionGen.generateOne
    val artifactFile       = Paths.get(ArtifactFileName(repositoryName, releaseVersion).toString)
    val workspaceDirectory = Paths.get(".")
    val slugDirectory      = Paths.get("slug")
    val startDockerFile    = slugDirectory resolve "start-docker.sh"
    val procFile           = slugDirectory resolve "Procfile"
    val slugRunnerVersion  = "0.5.2"
    val slugTgzFile        = Paths.get(s"${repositoryName}_${releaseVersion}_$slugRunnerVersion.tgz")

    val progressReporter = new ProgressReporter {

      var logs: List[String] = List.empty

      override def printError(message: String): Unit = {
        logs = logs :+ message
        super.printSuccess(message)
      }
      override def printSuccess(message: String): Unit = {
        logs = logs :+ message
        super.printSuccess(message)
      }
    }
    val artifactConnector        = mock[ArtifactoryConnector]
    val tarArchiver              = mock[TarArchiver]
    val startDockerScriptCreator = mock[StartDockerScriptCreator]
    val fileUtils                = mock[FileUtils]

    val jdkFileName = "jdk.tgz"

    val slugBuilder =
      new SlugBuilder(progressReporter, artifactConnector, tarArchiver, startDockerScriptCreator, fileUtils)

    when(artifactConnector.slugArtifactFileName(repositoryName, releaseVersion))
      .thenReturn(s"${repositoryName}_${releaseVersion}_$slugRunnerVersion.tgz")

    when(artifactConnector.verifySlugNotCreatedYet(repositoryName, releaseVersion))
      .thenReturn(Right("Slug does not exist"))

    when(
      artifactConnector
        .downloadArtifact(repositoryName, releaseVersion))
      .thenReturn(Right("Artifact downloaded"))

    when(artifactConnector.downloadAppConfigBase(repositoryName))
      .thenReturn(Right("app-config-base downloaded"))

    when(tarArchiver.decompress(artifactFile, slugDirectory))
      .thenReturn(Right(s"Successfully decompressed $artifactFile"))

    when(startDockerScriptCreator.ensureStartDockerExists(
      meq(workspaceDirectory), meq(slugDirectory), meq(repositoryName), any()))
      .thenReturn(Right("Created new start-docker.sh script"))

    when(artifactConnector.downloadJdk(jdkFileName))
      .thenReturn(Right(s"Successfully downloaded the JDK"))

    when(tarArchiver.decompress(Paths.get("jdk.tgz"), slugDirectory.resolve(".jdk")))
      .thenReturn(Right("Successfully decompressed jdk.tgz"))

    when(tarArchiver.compress(slugTgzFile, slugDirectory))
      .thenReturn(Right(s"Successfully compressed $slugTgzFile"))

    when(artifactConnector.publish(repositoryName, releaseVersion))
      .thenReturn(Right(s"Slug published successfully to https://artifactory/some-slug.tgz"))
  }
}
