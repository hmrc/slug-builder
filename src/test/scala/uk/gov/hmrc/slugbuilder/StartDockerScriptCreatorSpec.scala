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
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.{OpenOption, Path, Paths}
import org.scalamock.scalatest.MockFactory
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import uk.gov.hmrc.slugbuilder.generators.Generators.Implicits._
import uk.gov.hmrc.slugbuilder.generators.Generators.repositoryNameGen
import scala.language.implicitConversions

class StartDockerScriptCreatorSpec extends WordSpec with MockFactory {

  "ensureStartDockerExists" should {

    "do nothing if 'start-docker.sh' already exists" in new Setup {
      checkFileExist
        .expects(startDockerShInWorkspace)
        .returning(true)

      createDir
        .expects(confDirectory)
        .returning(())

      move
        .expects(appConfigBase, confDirectory resolve appConfigBase)
        .returning(())

      copy
        .expects(startDockerShInWorkspace, startDockerSh)
        .returning(())

      startDockerShCreator.ensureStartDockerExists(workspaceDirectory, slugDirectory, repositoryName) shouldBe
        Right("Successfully copied start-docker.sh from the workspace to the slug")
    }

    "create the 'conf' directory under 'slug', " +
      "move app-config-base into it and " +
      "create a new 'start-docker.sh'" +
      "if the 'start-docker.sh' doesn't exist" in new Setup {

      checkFileExist
        .expects(startDockerShInWorkspace)
        .returning(false)

      createDir
        .expects(confDirectory)
        .returning(())

      move
        .expects(appConfigBase, confDirectory resolve appConfigBase)
        .returning(())

      val startDockerContent = Seq(
        "#!/bin/sh",
        s"SCRIPT=$$(find . -type f -name $repositoryName)",
        s"exec $$SCRIPT $$HMRC_CONFIG -Dconfig.file=conf/${appConfigBase.toFile.getName}"
      )
      createFile
        .expects(startDockerSh, startDockerContent, UTF_8, CREATE_NEW)
        .returning(())

      startDockerShCreator.ensureStartDockerExists(workspaceDirectory, slugDirectory, repositoryName) shouldBe
        Right("Successfully created new start-docker.sh script")
    }

    "return error when check if start-docker.sh exists fails" in new Setup {

      val exception = new Exception("exception message")
      checkFileExist
        .expects(startDockerShInWorkspace)
        .throwing(exception)

      startDockerShCreator.ensureStartDockerExists(workspaceDirectory, slugDirectory, repositoryName) shouldBe Left(
        s"Couldn't check if $startDockerShInWorkspace exists. Cause: ${exception.getMessage}"
      )
    }
    "return error when copy of start-docker.sh fails" in new Setup {

      checkFileExist
        .expects(startDockerShInWorkspace)
        .returning(true)

      createDir
        .expects(confDirectory)
        .returning(())

      move
        .expects(appConfigBase, confDirectory resolve appConfigBase)
        .returning(())

      val exception = new Exception("exception message")
      copy
        .expects(startDockerShInWorkspace, startDockerSh)
        .throwing(exception)

      startDockerShCreator.ensureStartDockerExists(workspaceDirectory, slugDirectory, repositoryName) shouldBe Left(
        s"Couldn't copy the $startDockerShInWorkspace script to the slug directory. Cause: $exception"
      )
    }

    "return error when conf directory creation fails" in new Setup {

      checkFileExist
        .expects(startDockerShInWorkspace)
        .returning(false)

      val exception = new Exception("exception message")
      createDir
        .expects(confDirectory)
        .throwing(exception)

      startDockerShCreator.ensureStartDockerExists(workspaceDirectory, slugDirectory, repositoryName) shouldBe Left(
        s"Couldn't create conf directory at $confDirectory. Cause: ${exception.getMessage}"
      )
    }

    "return error when moving app-config-base to the conf directory fails" in new Setup {

      checkFileExist
        .expects(startDockerShInWorkspace)
        .returning(false)

      createDir
        .expects(confDirectory)
        .returning(())

      val exception = new Exception("exception message")
      move
        .expects(appConfigBase, confDirectory resolve appConfigBase)
        .throwing(exception)

      startDockerShCreator.ensureStartDockerExists(workspaceDirectory, slugDirectory, repositoryName) shouldBe Left(
        s"Couldn't move $appConfigBase to $confDirectory. Cause: $exception"
      )
    }

    "return error when creating start-docker.sh fails" in new Setup {

      checkFileExist
        .expects(startDockerShInWorkspace)
        .returning(false)

      createDir
        .expects(confDirectory)
        .returning(())

      move
        .expects(appConfigBase, confDirectory resolve appConfigBase)
        .returning(())

      val exception = new Exception("exception message")
      createFile
        .expects(startDockerSh, *, *, *)
        .throwing(exception)

      startDockerShCreator.ensureStartDockerExists(workspaceDirectory, slugDirectory, repositoryName) shouldBe Left(
        s"Couldn't create $startDockerSh. Cause: $exception"
      )
    }
  }

  private trait Setup {
    val repositoryName           = repositoryNameGen.generateOne
    val workspaceDirectory       = Paths.get(".")
    val slugDirectory            = Paths.get("slug")
    val startDockerShInWorkspace = workspaceDirectory resolve "start-docker.sh"
    val startDockerSh            = slugDirectory resolve "start-docker.sh"
    val confDirectory            = slugDirectory resolve "conf"
    val appConfigBase            = Paths.get(AppConfigBaseFileName(repositoryName).toString)

    val createDir            = mockFunction[Path, Unit]
    val checkFileExist       = mockFunction[Path, Boolean]
    val move                 = mockFunction[Path, Path, Unit]
    val copy                 = mockFunction[Path, Path, Unit]
    val createFile           = mockFunction[Path, Seq[String], Charset, OpenOption, Unit]
    val startDockerShCreator = new StartDockerScriptCreator(createDir, checkFileExist, move, copy, createFile)
  }

  private implicit def pathToFile(path: Path): File = path.toFile
}
