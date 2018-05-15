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
import java.nio.file.Paths

import org.rauschig.jarchivelib.Archiver
import org.scalamock.scalatest.MockFactory
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.slugbuilder.generators.Generators.Implicits._
import uk.gov.hmrc.slugbuilder.generators.Generators.{releaseVersionGen, repositoryNameGen}

class SlugFileAssemblerSpec extends WordSpec with ScalaFutures with MockFactory {

  "assembly" should {

    "create a slug directory and " +
      "extract the artifact into it" in new Setup {

      createDir
        .expects(slugDirectory)
        .returning(())

      (archiver
        .extract(_: File, _: File))
        .expects(artifactFile, slugDirectory)
        .returning(())

      assembler.assemble(repositoryName, releaseVersion).value.futureValue shouldBe Right(
        s"${artifactFile.getName} slug file assembled"
      )
    }

    "return error message when slug directory cannot be created" in new Setup {
      val exception = new Exception("exception message")
      createDir
        .expects(slugDirectory)
        .throwing(exception)

      assembler.assemble(repositoryName, releaseVersion).value.futureValue shouldBe Left(
        s"Couldn't create slug directory at ${slugDirectory.getName}. Cause: ${exception.getMessage}"
      )
    }

    "return error message when artifact cannot be extracted" in new Setup {
      createDir
        .expects(slugDirectory)
        .returning(())

      val exception = new Exception("exception message")
      (archiver
        .extract(_: File, _: File))
        .expects(artifactFile, slugDirectory)
        .throwing(exception)

      assembler.assemble(repositoryName, releaseVersion).value.futureValue shouldBe Left(
        s"Couldn't decompress artifact from ${artifactFile.getName}. Cause: ${exception.getMessage}"
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
    val artifactFile   = Paths.get(s"$repositoryName-$releaseVersion.tgz").toFile
    val slugDirectory  = Paths.get("slug").toFile

    val archiver = mock[Archiver]
    val createDir = mockFunction[File, Unit]

    val assembler = new SlugFileAssembler(archiver, createDir)
  }
}
