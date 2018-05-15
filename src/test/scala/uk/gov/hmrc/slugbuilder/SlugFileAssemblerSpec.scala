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
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermission._
import java.nio.file.{Path, Paths}

import org.rauschig.jarchivelib.Archiver
import org.scalamock.scalatest.MockFactory
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.slugbuilder.generators.Generators.Implicits._
import uk.gov.hmrc.slugbuilder.generators.Generators.{releaseVersionGen, repositoryNameGen}

import scala.language.implicitConversions

class SlugFileAssemblerSpec extends WordSpec with ScalaFutures with MockFactory {

  "assembly" should {

    "create a 'slug' directory, " +
      "extract the artifact into it, " +
      "assure 'start-docker.sh' file exist in the 'slug' directory and " +
      "everyone has execute privileges on it " in new Setup {

      createDir
        .expects(slugDirectory)
        .returning(())

      (archiver
        .extract(_: File, _: File))
        .expects(artifactFile.toFile, slugDirectory.toFile)
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
        .extract(_: File, _: File))
        .expects(artifactFile.toFile, slugDirectory.toFile)
        .throwing(exception)

      assembler.assemble(repositoryName, releaseVersion).value.futureValue shouldBe Left(
        s"Couldn't decompress artifact from ${artifactFile.toFile.getName}. Cause: ${exception.getMessage}"
      )
    }

    "return error when start-docker.sh creation return left" in new Setup {
      createDir
        .expects(slugDirectory)
        .returning(())

      (archiver
        .extract(_: File, _: File))
        .expects(artifactFile.toFile, slugDirectory.toFile)
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
        .extract(_: File, _: File))
        .expects(artifactFile.toFile, slugDirectory.toFile)
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
    val artifactFile   = Paths.get(s"$repositoryName-$releaseVersion.tgz")
    val slugDirectory  = Paths.get("slug")
    val startDockerFile  = slugDirectory resolve "start-docker.sh"

    val archiver             = mock[Archiver]
    val startDockerShCreator = mock[StartDockerScriptCreator]
    val createDir            = mockFunction[Path, Unit]
    val setPermissions       = mockFunction[Path, Set[PosixFilePermission], Unit]
    val assembler            = new SlugFileAssembler(archiver, startDockerShCreator, createDir, setPermissions)
  }

  private implicit def pathToFile(path: Path): File = path.toFile
}
