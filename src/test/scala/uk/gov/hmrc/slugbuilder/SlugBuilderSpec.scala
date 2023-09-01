/*
 * Copyright 2023 HM Revenue & Customs
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

import org.mockito.scalatest.MockitoSugar
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.slugbuilder.connectors.ArtifactoryConnector
import uk.gov.hmrc.slugbuilder.generators.Generators.Implicits._
import uk.gov.hmrc.slugbuilder.generators.Generators._
import uk.gov.hmrc.slugbuilder.tools.{FileUtils, TarArchiver}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Path, Paths}
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.attribute.PosixFilePermission._


class SlugBuilderSpec
  extends AnyWordSpec
     with Matchers
     with MockitoSugar
     with EitherValues {

  "create" should {
    "finish successfully if " +
      "slug for the given version of the microservice does not exist and " +
      "microservice artefact can be downloaded and " +
      "app-config-base can be downloaded and " +
      "a slug file is assembled" in new Setup {

      slugBuilder.create(
        repositoryName,
        releaseVersion,
        slugRuntimeJavaOpts = None,
        buildProperties     = Map("a" -> "b", "c" -> "d"),
        includeFiles        = Some("path/file1"),
        artefactLocation    = None,
        publish             = false
      ).value shouldBe ()

      progressReporter.logs should contain("Artefact downloaded")
      progressReporter.logs should contain("Successfully downloaded the JDK")
      progressReporter.logs should contain("Successfully decompressed jdk.tgz")
      progressReporter.logs should contain("Successfully created .profile.d/java.sh")
      progressReporter.logs should contain(s"Successfully compressed $slugTgzFile")
      progressReporter.logs should not contain("Slug published successfully to https://artifactory/some-slug.tgz")

      verify(startDockerScriptCreator).ensureStartDockerExists(workspaceDirectory, slugDirectory, repositoryName, None)

      verify(fileUtils).createDir(slugDirectory)

      verify(fileUtils)
        .setPermissions(
          startDockerFile,
          Set(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ)
        )

      verify(fileUtils).createFile(procFile, "web: ./start-docker.sh", UTF_8, CREATE_NEW)

      verify(fileUtils).createFile(buildPropertiesFile, "a=b\nc=d", UTF_8, CREATE_NEW)

      verify(fileUtils).copyFile(Paths.get("path/file1"), slugDirectory)

      verify(fileUtils).createDir(slugDirectory.resolve(".jdk"))

      val profileD: Path = slugDirectory.resolve(".profile.d")
      verify(fileUtils).createDir(profileD)

      val javaSh: Path = profileD.resolve("java.sh")
      verify(fileUtils)
        .createFile(javaSh, "PATH=$HOME/.jdk/bin:$PATH\nJAVA_HOME=$HOME/.jdk/", UTF_8, CREATE_NEW)

      verify(artifactoryConnector, never)
        .publish(*, *)
    }

    "finishes successfully and does not copy sensitive environment variables to build.properties" in new Setup {
      private val environmentVariables = Map(
        "a"                    -> "b",
        "GITHUB_TOKEN"         -> "token",
        "auth_password"        -> "password",
        "SECRET"               -> "secret",
        "USERNAME"             -> "username",
        "ACCESS_KEY"           -> "key",
        "HUDSON_SERVER_COOKIE" -> "cookie"
      )

      slugBuilder.create(
        repositoryName,
        releaseVersion,
        slugRuntimeJavaOpts   = None,
        environmentVariables,
        includeFiles          = None,
        artefactLocation      = None,
        publish               = false
      ).value shouldBe ()

      progressReporter.logs should contain("Artefact downloaded")
      progressReporter.logs should contain("Successfully downloaded the JDK")
      progressReporter.logs should contain("Successfully decompressed jdk.tgz")
      progressReporter.logs should contain("Successfully created .profile.d/java.sh")
      progressReporter.logs should contain(s"Successfully compressed $slugTgzFile")

      verify(startDockerScriptCreator).ensureStartDockerExists(workspaceDirectory, slugDirectory, repositoryName, None)

      verify(fileUtils).createDir(slugDirectory)

      verify(fileUtils)
        .setPermissions(
          startDockerFile,
          Set(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ)
        )

      verify(fileUtils).createFile(procFile, "web: ./start-docker.sh", UTF_8, CREATE_NEW)

      verify(fileUtils).createFile(buildPropertiesFile, "a=b", UTF_8, CREATE_NEW)

      verify(fileUtils).createDir(slugDirectory.resolve(".jdk"))

      val profileD: Path = slugDirectory.resolve(".profile.d")
      verify(fileUtils).createDir(profileD)

      val javaSh: Path = profileD.resolve("java.sh")
      verify(fileUtils)
        .createFile(javaSh, "PATH=$HOME/.jdk/bin:$PATH\nJAVA_HOME=$HOME/.jdk/", UTF_8, CREATE_NEW)
    }

    "finish successfully if " +
      "slug for the given version of the microservice does not exist and " +
      "microservice artefact can be downloaded and " +
      "app-config-base can be downloaded and " +
      "a slug file is assembled" +
      "and custom JAVA_OPTS property has been provided" in new Setup {

      slugBuilder.create(
        repositoryName,
        releaseVersion,
        Some(SlugRuntimeJavaOpts("-Xmx256")),
        buildProperties       = Map("a" -> "b", "c" -> "d"),
        includeFiles          = None,
        artefactLocation      = None,
        publish               = false
      ).value shouldBe ()

      progressReporter.logs should contain("Artefact downloaded")
      progressReporter.logs should contain("Successfully downloaded the JDK")
      progressReporter.logs should contain("Successfully decompressed jdk.tgz")
      progressReporter.logs should contain("Successfully created .profile.d/java.sh")
      progressReporter.logs should contain(s"Successfully compressed $slugTgzFile")

      verify(startDockerScriptCreator).ensureStartDockerExists(
        workspaceDirectory,
        slugDirectory,
        repositoryName,
        Some(SlugRuntimeJavaOpts("-Xmx256"))
      )

      verify(fileUtils).createDir(slugDirectory)

      verify(fileUtils)
        .setPermissions(
          startDockerFile,
          Set(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ)
        )

      verify(fileUtils).createFile(procFile, "web: ./start-docker.sh", UTF_8, CREATE_NEW)

      verify(fileUtils).createFile(buildPropertiesFile, "a=b\nc=d", UTF_8, CREATE_NEW)

      verify(fileUtils).createDir(slugDirectory.resolve(".jdk"))

      val profileD: Path = slugDirectory.resolve(".profile.d")
      verify(fileUtils).createDir(profileD)

      val javaSh: Path = profileD.resolve("java.sh")
      verify(fileUtils)
        .createFile(javaSh, "PATH=$HOME/.jdk/bin:$PATH\nJAVA_HOME=$HOME/.jdk/", UTF_8, CREATE_NEW)
    }

    "publish" in new Setup {
      when(artifactoryConnector.publish(repositoryName, releaseVersion))
        .thenReturn(Right(s"Slug published successfully to https://artifactory/some-slug.tgz"))

      slugBuilder.create(
        repositoryName,
        releaseVersion,
        slugRuntimeJavaOpts = None,
        buildProperties     = Map("a" -> "b", "c" -> "d"),
        includeFiles        = Some("path/file1"),
        artefactLocation    = None,
        publish             = true
      ).value shouldBe ()

      progressReporter.logs should contain("Slug does not exist")
      progressReporter.logs should contain("Artefact downloaded")
      progressReporter.logs should contain("Successfully downloaded the JDK")
      progressReporter.logs should contain("Successfully decompressed jdk.tgz")
      progressReporter.logs should contain("Successfully created .profile.d/java.sh")
      progressReporter.logs should contain(s"Successfully compressed $slugTgzFile")
      progressReporter.logs should contain("Slug published successfully to https://artifactory/some-slug.tgz")

      verify(startDockerScriptCreator).ensureStartDockerExists(workspaceDirectory, slugDirectory, repositoryName, None)

      verify(fileUtils).createDir(slugDirectory)

      verify(fileUtils)
        .setPermissions(
          startDockerFile,
          Set(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ)
        )

      verify(fileUtils).createFile(procFile, "web: ./start-docker.sh", UTF_8, CREATE_NEW)

      verify(fileUtils).createFile(buildPropertiesFile, "a=b\nc=d", UTF_8, CREATE_NEW)

      verify(fileUtils).copyFile(Paths.get("path/file1"), slugDirectory)

      verify(fileUtils).createDir(slugDirectory.resolve(".jdk"))

      val profileD: Path = slugDirectory.resolve(".profile.d")
      verify(fileUtils).createDir(profileD)

      val javaSh: Path = profileD.resolve("java.sh")
      verify(fileUtils)
        .createFile(javaSh, "PATH=$HOME/.jdk/bin:$PATH\nJAVA_HOME=$HOME/.jdk/", UTF_8, CREATE_NEW)

      verify(artifactoryConnector)
        .publish(repositoryName, releaseVersion)
    }

    "not create the slug if it already exists in the Webstore when publishing" in new Setup {
      when(artifactoryConnector.verifySlugNotCreatedYet(repositoryName, releaseVersion))
        .thenReturn(Left("Slug does exist"))

      slugBuilder.create(
        repositoryName,
        releaseVersion,
        slugRuntimeJavaOpts = None,
        buildProperties     = Map.empty,
        includeFiles        = None,
        artefactLocation    = None,
        publish             = true
      ).left.value shouldBe ()
      progressReporter.logs should contain("Slug does exist")
    }

    "not create the slug if there is no artefact in the Artifactory" in new Setup {
      when(
        artifactoryConnector.downloadArtefact(repositoryName, releaseVersion, ArtefactFileName(repositoryName, releaseVersion))
      ).thenReturn(Left("Artefact does not exist"))

      slugBuilder.create(
        repositoryName,
        releaseVersion,
        slugRuntimeJavaOpts = None,
        buildProperties     = Map.empty,
        includeFiles        = None,
        artefactLocation    = None,
        publish             = false
      ).left.value shouldBe ()
      progressReporter.logs should contain("Artefact does not exist")
    }

    "return error message when slug directory cannot be created" in new Setup {
      val exception = new RuntimeException("exception message")
      doThrow(exception).when(fileUtils).createDir(slugDirectory)

      slugBuilder.create(
        repositoryName,
        releaseVersion,
        slugRuntimeJavaOpts = None,
        buildProperties     = Map.empty,
        includeFiles        = None,
        artefactLocation    = None,
        publish             = false
      ).left.value shouldBe ()
      progressReporter.logs should contain(
        s"Couldn't create slug directory at ${slugDirectory.toFile.getName}. Cause: ${exception.getMessage}")
    }

    "return error message when artefact cannot be extracted" in new Setup {
      when(tarArchiver.decompress(artefactFile, slugDirectory))
        .thenReturn(Left("Some error"))

      slugBuilder.create(
        repositoryName,
        releaseVersion,
        slugRuntimeJavaOpts = None,
        buildProperties     = Map.empty,
        includeFiles        = None,
        artefactLocation    = None,
        publish             = false
      ).left.value shouldBe ()
      progressReporter.logs should contain("Some error")
    }

    "return error when start-docker.sh creation return left" in new Setup {
      val errorMessage = "error message"
      when(startDockerScriptCreator.ensureStartDockerExists(workspaceDirectory, slugDirectory, repositoryName, None))
        .thenReturn(Left(errorMessage))

      slugBuilder.create(
        repositoryName,
        releaseVersion,
        slugRuntimeJavaOpts = None,
        buildProperties     = Map.empty,
        includeFiles        = None,
        artefactLocation    = None,
        publish             = false
      ).left.value shouldBe ()
      progressReporter.logs should contain("error message")
    }

    "return error when start-docker.sh permissions cannot be changed" in new Setup {
      val exception = new RuntimeException("exception message")
      doThrow(exception)
        .when(fileUtils)
        .setPermissions(
          startDockerFile,
          Set(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ))

      slugBuilder.create(
        repositoryName,
        releaseVersion,
        slugRuntimeJavaOpts = None,
        buildProperties     = Map.empty,
        includeFiles        = None,
        artefactLocation    = None,
        publish             = false
      ).left.value shouldBe ()
      progressReporter.logs should contain(
        s"Couldn't change permissions of the $startDockerFile. Cause: ${exception.getMessage}")
    }

    "return error if creating the Procfile fails" in new Setup {
      val exception = new RuntimeException("exception message")
      doThrow(exception).when(fileUtils).createFile(procFile, "web: ./start-docker.sh", UTF_8, CREATE_NEW)

      slugBuilder.create(
        repositoryName,
        releaseVersion,
        slugRuntimeJavaOpts = None,
        buildProperties     = Map.empty,
        includeFiles        = None,
        artefactLocation    = None,
        publish             = false
      ).left.value shouldBe ()
      progressReporter.logs should contain(s"Couldn't create the $procFile. Cause: ${exception.getMessage}")
    }

    "return error if creating the .jdk directory fails" in new Setup {
      val exception = new RuntimeException("exception message")
      doThrow(exception).when(fileUtils).createDir(slugDirectory.resolve(".jdk"))

      slugBuilder.create(
        repositoryName,
        releaseVersion,
        slugRuntimeJavaOpts = None,
        buildProperties     = Map.empty,
        includeFiles        = None,
        artefactLocation    = None,
        publish             = false
      ).left.value shouldBe ()
      progressReporter.logs should contain(
        s"Couldn't create .jdk directory at $slugDirectory/.jdk. Cause: ${exception.getMessage}")
    }

    "return error if downloading the JDK fails" in new Setup {
      when(artifactoryConnector.downloadJdk(jdkFileName))
        .thenReturn(Left("Error downloading JDK"))

      slugBuilder.create(
        repositoryName,
        releaseVersion,
        slugRuntimeJavaOpts = None,
        buildProperties     = Map.empty,
        includeFiles        = None,
        artefactLocation    = None,
        publish             = false
      ).left.value shouldBe ()
      progressReporter.logs should contain("Error downloading JDK")
    }

    "return error message when the JDK cannot be extracted" in new Setup {
      when(tarArchiver.decompress(Paths.get(jdkFileName), slugDirectory.resolve(".jdk")))
        .thenReturn(Left("Some error"))

      slugBuilder.create(
        repositoryName,
        releaseVersion,
        slugRuntimeJavaOpts = None,
        buildProperties     = Map.empty,
        includeFiles        = None,
        artefactLocation    = None,
        publish             = false
      ).left.value shouldBe ()
      progressReporter.logs should contain("Some error")
    }

    "return error message when the Slug cannot be compressed" in new Setup {
      when(tarArchiver.compress(slugTgzFile, slugDirectory))
        .thenReturn(Left("Some error"))

      slugBuilder.create(
        repositoryName,
        releaseVersion,
        slugRuntimeJavaOpts = None,
        buildProperties     = Map.empty,
        includeFiles        = None,
        artefactLocation    = None,
        publish             = false
      ).left.value shouldBe ()
      progressReporter.logs should contain(s"Some error")
    }

    "return error message when the Slug cannot be published" in new Setup {
      when(artifactoryConnector.publish(repositoryName, releaseVersion))
        .thenReturn(Left("Some error"))

      slugBuilder.create(
        repositoryName,
        releaseVersion,
        slugRuntimeJavaOpts = None,
        buildProperties     = Map.empty,
        includeFiles        = None,
        artefactLocation    = None,
        publish             = true
      ).left.value shouldBe ()
      progressReporter.logs should contain(s"Some error")
    }

    "not catch fatal Errors" in new Setup {
      val error = new OutOfMemoryError("error")
      doThrow(error).when(fileUtils).createDir(slugDirectory)

      intercept[Throwable](
        slugBuilder.create(
          repositoryName,
          releaseVersion,
          slugRuntimeJavaOpts = None,
          buildProperties     = Map.empty,
          includeFiles        = None,
          artefactLocation    = None,
          publish             = false
        )
      ) shouldBe error
    }
  }

  private trait Setup {
    val repositoryName      = repositoryNameGen.generateOne
    val releaseVersion      = releaseVersionGen.generateOne
    val artefactFile        = Paths.get(ArtefactFileName(repositoryName, releaseVersion).toString)
    val workspaceDirectory  = Paths.get(".")
    val slugDirectory       = Paths.get("slug")
    val startDockerFile     = slugDirectory.resolve("start-docker.sh")
    val procFile            = slugDirectory.resolve("Procfile")
    val buildPropertiesFile = slugDirectory.resolve("build.properties")
    val slugRunnerVersion   = "0.5.2"
    val slugTgzFile         = Paths.get(s"${repositoryName}_${releaseVersion}_$slugRunnerVersion.tgz")

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
    val artifactoryConnector     = mock[ArtifactoryConnector    ](withSettings.lenient)
    val tarArchiver              = mock[TarArchiver             ](withSettings.lenient)
    val startDockerScriptCreator = mock[StartDockerScriptCreator](withSettings.lenient)
    val fileUtils                = mock[FileUtils               ](withSettings.lenient)

    val jdkFileName = "jdk.tgz"

    val slugBuilder =
      new SlugBuilder(
        progressReporter,
        artifactoryConnector,
        tarArchiver,
        startDockerScriptCreator,
        fileUtils
      )

    when(artifactoryConnector.slugArtefactFileName(repositoryName, releaseVersion))
      .thenReturn(s"${repositoryName}_${releaseVersion}_$slugRunnerVersion.tgz")

    when(artifactoryConnector.verifySlugNotCreatedYet(repositoryName, releaseVersion))
      .thenReturn(Right("Slug does not exist"))

    when(artifactoryConnector.downloadArtefact(repositoryName, releaseVersion, ArtefactFileName(repositoryName, releaseVersion)))
      .thenReturn(Right("Artefact downloaded"))

    when(tarArchiver.decompress(artefactFile, slugDirectory))
      .thenReturn(Right(s"Successfully decompressed $artefactFile"))

    when(startDockerScriptCreator.ensureStartDockerExists(eqTo(workspaceDirectory), eqTo(slugDirectory), eqTo(repositoryName), any))
      .thenReturn(Right("Created new start-docker.sh script"))

    when(artifactoryConnector.downloadJdk(jdkFileName))
      .thenReturn(Right(s"Successfully downloaded the JDK"))

    when(tarArchiver.decompress(Paths.get("jdk.tgz"), slugDirectory.resolve(".jdk")))
      .thenReturn(Right("Successfully decompressed jdk.tgz"))

    when(tarArchiver.compress(slugTgzFile, slugDirectory))
      .thenReturn(Right(s"Successfully compressed $slugTgzFile"))
  }
}
