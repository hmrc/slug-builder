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
import java.nio.file.{OpenOption, Path, Paths}
import java.util.stream.{Stream => JavaStream}

import org.scalamock.scalatest.MockFactory
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.slugbuilder.generators.Generators.Implicits._
import uk.gov.hmrc.slugbuilder.generators.Generators.{releaseVersionGen, repositoryNameGen}
import uk.gov.hmrc.slugbuilder.tools.TarArchiver

import scala.language.implicitConversions

class SlugFileAssemblerSpec extends WordSpec with ScalaFutures with MockFactory {

  "assembly" should {

    "create a 'slug' directory, " +
      "extract the artifact into it, " +
      "assure 'start-docker.sh' file exist in the 'slug' directory and " +
      "it has 755 permissions and " +
      "a 'Procfile' is created in the 'slug' directory" in new Setup {

      createDir
        .expects(slugDirectory)
        .returning(())

      (archiver
        .decompress(_: Path, _: Path))
        .expects(artifactFile, slugDirectory)
        .returning(())

      (startDockerShCreator
        .ensureStartDockerExists(_: Path, _: RepositoryName))
        .expects(slugDirectory, repositoryName)
        .returning(Right(()))

      setPermissions
        .expects(
          startDockerFile,
          Set(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ))
        .returning(())

      createFile
        .expects(procFile, "web: ./start-docker.sh", UTF_8, CREATE_NEW)
        .returning(())

      val filesInSlugDir: JavaStream[Path] = JavaStream.empty()
      listFiles
        .expects(slugDirectory)
        .returning(filesInSlugDir)

      (archiver
        .tar(_: Path, _: JavaStream[Path]))
        .expects(slugTarFile, filesInSlugDir)
        .returning(())

      assembler.assemble(repositoryName, releaseVersion).value.futureValue shouldBe Right(
        s"${artifactFile.toFile.getName} slug file assembled"
      )
    }

    "return error message when slug directory cannot be created" in new Setup {
      val exception = new Exception("exception message")
      createDir
        .expects(slugDirectory)
        .throwing(exception)

      assembler.assemble(repositoryName, releaseVersion).value.futureValue shouldBe Left(
        s"Couldn't create slug directory at ${slugDirectory.toFile.getName}. Cause: ${exception.getMessage}"
      )
    }

    "return error message when artifact cannot be extracted" in new Setup {
      createDir
        .expects(slugDirectory)
        .returning(())

      val exception = new Exception("exception message")
      (archiver
        .decompress(_: Path, _: Path))
        .expects(artifactFile, slugDirectory)
        .throwing(exception)

      assembler.assemble(repositoryName, releaseVersion).value.futureValue shouldBe Left(
        s"Couldn't decompress artifact from ${artifactFile.toFile.getName}. Cause: $exception"
      )
    }

    "return error when start-docker.sh creation return left" in new Setup {
      createDir
        .expects(slugDirectory)
        .returning(())

      (archiver
        .decompress(_: Path, _: Path))
        .expects(artifactFile, slugDirectory)
        .returning(())

      val errorMessage = "error message"
      (startDockerShCreator
        .ensureStartDockerExists(_: Path, _: RepositoryName))
        .expects(slugDirectory, repositoryName)
        .returning(Left(errorMessage))

      assembler.assemble(repositoryName, releaseVersion).value.futureValue shouldBe Left(errorMessage)
    }

    "return error when start-docker.sh permissions cannot be changed" in new Setup {
      createDir
        .expects(slugDirectory)
        .returning(())

      (archiver
        .decompress(_: Path, _: Path))
        .expects(artifactFile, slugDirectory)
        .returning(())

      (startDockerShCreator
        .ensureStartDockerExists(_: Path, _: RepositoryName))
        .expects(slugDirectory, repositoryName)
        .returning(Right(()))

      val exception = new Exception("exception message")
      setPermissions
        .expects(
          startDockerFile,
          Set(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ))
        .throwing(exception)

      assembler.assemble(repositoryName, releaseVersion).value.futureValue shouldBe Left(
        s"Couldn't change permissions of the $startDockerFile. Cause: ${exception.getMessage}"
      )
    }

    "return error if creating the Procfile fails" in new Setup {
      createDir
        .expects(slugDirectory)
        .returning(())

      (archiver
        .decompress(_: Path, _: Path))
        .expects(artifactFile, slugDirectory)
        .returning(())

      (startDockerShCreator
        .ensureStartDockerExists(_: Path, _: RepositoryName))
        .expects(slugDirectory, repositoryName)
        .returning(Right(()))

      setPermissions
        .expects(
          startDockerFile,
          Set(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ))
        .returning(())

      val exception = new Exception("exception message")
      createFile
        .expects(procFile, "web: ./start-docker.sh", UTF_8, CREATE_NEW)
        .throwing(exception)

      assembler.assemble(repositoryName, releaseVersion).value.futureValue shouldBe Left(
        s"Couldn't create the $procFile. Cause: ${exception.getMessage}"
      )
    }

    "return error if creating the slug tar fails" in new Setup {
      createDir
        .expects(slugDirectory)
        .returning(())

      (archiver
        .decompress(_: Path, _: Path))
        .expects(artifactFile, slugDirectory)
        .returning(())

      (startDockerShCreator
        .ensureStartDockerExists(_: Path, _: RepositoryName))
        .expects(slugDirectory, repositoryName)
        .returning(Right(()))

      setPermissions
        .expects(
          startDockerFile,
          Set(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ))
        .returning(())

      createFile
        .expects(procFile, "web: ./start-docker.sh", UTF_8, CREATE_NEW)
        .returning(())

      val filesInSlugDir: JavaStream[Path] = JavaStream.empty()
      listFiles
        .expects(slugDirectory)
        .returning(filesInSlugDir)

      val exception = new Exception("exception message")
      (archiver
        .tar(_: Path, _: JavaStream[Path]))
        .expects(slugTarFile, filesInSlugDir)
        .throwing(exception)

      assembler.assemble(repositoryName, releaseVersion).value.futureValue shouldBe Left(
        s"Couldn't create the $slugTarFile. Cause: $exception"
      )
    }

    "not catch fatal Errors" in new Setup {
      val error = new OutOfMemoryError("error")
      createDir
        .expects(slugDirectory)
        .throwing(error)

      intercept[Throwable](assembler.assemble(repositoryName, releaseVersion).value.futureValue) shouldBe error
    }
  }

  private trait Setup {
    val repositoryName = repositoryNameGen.generateOne
    val releaseVersion = releaseVersionGen.generateOne
    val artifactFile = Paths.get(s"$repositoryName-$releaseVersion.tgz")
    val slugDirectory = Paths.get("slug")
    val startDockerFile = slugDirectory resolve "start-docker.sh"
    val procFile = slugDirectory resolve "Procfile"
    val slugTarFile = Paths.get(s"$repositoryName-$releaseVersion.tar")

    val archiver = mock[TarArchiver]
    val startDockerShCreator = mock[StartDockerScriptCreator]
    val createDir = mockFunction[Path, Unit]
    val setPermissions = mockFunction[Path, Set[PosixFilePermission], Unit]
    val createFile = mockFunction[Path, String, Charset, OpenOption, Unit]
    val listFiles = mockFunction[Path, JavaStream[Path]]
    val assembler = new SlugFileAssembler(archiver, startDockerShCreator, createDir, setPermissions, createFile, listFiles)
  }
}
