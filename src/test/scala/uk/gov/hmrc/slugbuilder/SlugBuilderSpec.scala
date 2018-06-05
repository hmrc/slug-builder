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
import org.mockito.Mockito
import org.mockito.Mockito.{doNothing, doThrow, reset, when}
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.PropertyChecks
import uk.gov.hmrc.slugbuilder.functions.ArtifactFileName
import uk.gov.hmrc.slugbuilder.generators.Generators.Implicits._
import uk.gov.hmrc.slugbuilder.generators.Generators._
import uk.gov.hmrc.slugbuilder.tools.{FileUtils, TarArchiver}

class SlugBuilderSpec extends WordSpec with PropertyChecks with MockitoSugar {

  "create" should {

    "finish successfully if " +
      "slug for the given version of the microservice does not exist and " +
      "microservice artifact can be downloaded and " +
      "app-config-base can be downloaded and " +
      "a slug file is assembled" in new Setup {

      slugBuilder.create(repositoryName, releaseVersion) should be('right)

      progressReporter.logs should contain("Slug does not exist")
      progressReporter.logs should contain("Artifact downloaded")
      progressReporter.logs should contain("app-config-base downloaded")

    }

    "not create the slug if it already exists in the Webstore" in new Setup {
      when(slugChecker.verifySlugNotCreatedYet(repositoryName, releaseVersion))
        .thenReturn(Left("Slug does exist"))

      slugBuilder.create(repositoryName, releaseVersion) should be('left)
      progressReporter.logs                              should contain("Slug does exist")
    }

    "not create the slug if there is no artifact in the Artifactory" in new Setup {

      when(artifactFetcher.download(repositoryName, releaseVersion))
        .thenReturn(Left("Artifact does not exist"))

      slugBuilder.create(repositoryName, releaseVersion) should be('left)
      progressReporter.logs                              should contain("Artifact does not exist")
    }

    "not create the slug if there is app-config-base in the Webstore" in new Setup {

      when(appConfigBaseFetcher.download(repositoryName))
        .thenReturn(Left("app-config-base does not exist"))

      slugBuilder.create(repositoryName, releaseVersion) should be('left)
      progressReporter.logs                              should contain("app-config-base does not exist")
    }

    "return error message when slug directory cannot be created" in new Setup {

      val exception = new RuntimeException("exception message")
      doThrow(exception).when(fileUtils).createDir(slugDirectory)

      slugBuilder.create(repositoryName, releaseVersion) should be('left)
      progressReporter.logs should contain(
        s"Couldn't create slug directory at ${slugDirectory.toFile.getName}. Cause: ${exception.getMessage}")
    }

    "return error message when artifact cannot be extracted" in new Setup {
      val exception = new RuntimeException("exception message")
      doThrow(exception).when(tarArchiver).decompress(artifactFile, slugDirectory)

      slugBuilder.create(repositoryName, releaseVersion) should be('left)
      progressReporter.logs should contain(
        s"Couldn't decompress artifact from ${artifactFile.toFile.getName}. Cause: $exception")
    }

    "return error when start-docker.sh creation return left" in new Setup {

      val errorMessage = "error message"
      when(startDockerScriptCreator.ensureStartDockerExists(slugDirectory, repositoryName))
        .thenReturn(Left(errorMessage))

      slugBuilder.create(repositoryName, releaseVersion) should be('left)
      progressReporter.logs                              should contain("error message")
    }

    "return error when start-docker.sh permissions cannot be changed" in new Setup {
      val exception = new RuntimeException("exception message")
      doThrow(exception)
        .when(fileUtils)
        .setPermissions(
          startDockerFile,
          Set(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ))

      slugBuilder.create(repositoryName, releaseVersion) should be('left)
      progressReporter.logs should contain(
        s"Couldn't change permissions of the $startDockerFile. Cause: ${exception.getMessage}")
    }

    "return error if creating the Procfile fails" in new Setup {
      val exception = new RuntimeException("exception message")
      doThrow(exception).when(fileUtils).createFile(procFile, "web: ./start-docker.sh", UTF_8, CREATE_NEW)

      slugBuilder.create(repositoryName, releaseVersion) should be('left)
      progressReporter.logs                              should contain(s"Couldn't create the $procFile. Cause: ${exception.getMessage}")
    }

    "return error if creating the .jdk directory fails" in new Setup {
      val exception = new RuntimeException("exception message")
      doThrow(exception).when(fileUtils).createDir(slugDirectory.resolve(".jdk"))

      slugBuilder.create(repositoryName, releaseVersion) should be('left)
      progressReporter.logs should contain(
        s"Couldn't create .jdk directory at $slugDirectory/.jdk. Cause: ${exception.getMessage}")
    }

    "return error if downloading the JDK fails" in new Setup {
      when(jdkFetcher.download)
        .thenReturn(Left("Error downloading JDK"))

      slugBuilder.create(repositoryName, releaseVersion) should be('left)
      progressReporter.logs should contain(
        s"Couldn't download the JDK from ${jdkFetcher.javaDownloadUri}. Cause: Error downloading JDK")
    }

    "not catch fatal Errors" in new Setup {
      val error = new OutOfMemoryError("error")
      doThrow(error).when(fileUtils).createDir(slugDirectory)

      intercept[Throwable](slugBuilder.create(repositoryName, releaseVersion)) shouldBe error
    }

  }

  private trait Setup {
    val repositoryName  = repositoryNameGen.generateOne
    val releaseVersion  = releaseVersionGen.generateOne
    val artifactFile    = Paths.get(ArtifactFileName(repositoryName, releaseVersion))
    val slugDirectory   = Paths.get("slug")
    val startDockerFile = slugDirectory resolve "start-docker.sh"
    val procFile        = slugDirectory resolve "Procfile"
    val slugTarFile     = Paths.get(s"$repositoryName-$releaseVersion.tar")
    val javaDownloadUri = "javaDownloadUri"

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
    val slugChecker              = mock[SlugChecker]
    val artifactFetcher          = mock[ArtifactFetcher]
    val appConfigBaseFetcher     = mock[AppConfigBaseFetcher]
    val jdkFetcher               = mock[JdkFetcher]
    val tarArchiver              = mock[TarArchiver]
    val startDockerScriptCreator = mock[StartDockerScriptCreator]
    val fileUtils                = mock[FileUtils]

    val slugBuilder = new SlugBuilder(
      progressReporter,
      slugChecker,
      artifactFetcher,
      appConfigBaseFetcher,
      jdkFetcher,
      tarArchiver,
      startDockerScriptCreator,
      fileUtils)

    when(slugChecker.verifySlugNotCreatedYet(repositoryName, releaseVersion))
      .thenReturn(Right("Slug does not exist"))

    when(artifactFetcher.download(repositoryName, releaseVersion))
      .thenReturn(Right("Artifact downloaded"))

    when(appConfigBaseFetcher.download(repositoryName))
      .thenReturn(Right("app-config-base downloaded"))

    doNothing().when(fileUtils).createDir(slugDirectory)

    doNothing().when(tarArchiver).decompress(artifactFile, slugDirectory)

    when(startDockerScriptCreator.ensureStartDockerExists(slugDirectory, repositoryName))
      .thenReturn(Right(()))

    doNothing()
      .when(fileUtils)
      .setPermissions(
        startDockerFile,
        Set(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ))

    doNothing().when(fileUtils).createFile(procFile, "web: ./start-docker.sh", UTF_8, CREATE_NEW)

    doNothing().when(fileUtils).createDir(slugDirectory.resolve(".jdk"))

    when(jdkFetcher.download)
      .thenReturn(Right(s"Successfully downloaded JDK from $javaDownloadUri"))

  }
}
