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

import java.nio.file.Paths

import org.eclipse.jgit.api.{CloneCommand, Git}
import org.mockito.Mockito._
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.slugbuilder.generators.Generators.Implicits._
import uk.gov.hmrc.slugbuilder.generators.Generators._

class DockerImageSpec extends WordSpec with ScalaFutures with MockitoSugar {

  "create" should {

    "clone 'buildpack-java-jar' repo" in new Setup {

      when(gitCloneCommand.setURI(repositoryUrl))
        .thenReturn(gitCloneCommand)

      when(gitCloneCommand.setDirectory(Paths.get("bp").toFile))
        .thenReturn(gitCloneCommand)

      val git = mock[Git]
      when(gitCloneCommand.call())
        .thenReturn(git)

      dockerImage.create(repositoryName, releaseVersion).value.futureValue shouldBe Right(
        s"Docker image ${repositoryName}_${releaseVersion}_$slugBuilderVersion.tgz created")
    }

    "return an error if cloning 'buildpack-java-jar' repo from GitHub fails" in new Setup {

      when(gitCloneCommand.setURI(repositoryUrl))
        .thenReturn(gitCloneCommand)

      when(gitCloneCommand.setDirectory(Paths.get("bp").toFile))
        .thenReturn(gitCloneCommand)

      val exception = new RuntimeException("error")
      when(gitCloneCommand.call())
        .thenThrow(exception)

      dockerImage.create(repositoryName, releaseVersion).value.futureValue shouldBe Left(
        s"Couldn't clone github.com/hmrc/buildpack-java-jar.git. Cause: ${exception.getMessage}"
      )
    }
  }

  private trait Setup {
    val repositoryName     = repositoryNameGen.generateOne
    val releaseVersion     = releaseVersionGen.generateOne
    val githubApiUser      = nonEmptyStrings.generateOne
    val githubApiToken     = nonEmptyStrings.generateOne
    val slugBuilderVersion = nonEmptyStrings.generateOne
    var repositoryUrl      = s"https://$githubApiUser:$githubApiToken@github.com/hmrc/buildpack-java-jar.git"

    val gitCloneCommand = mock[CloneCommand]

    val dockerImage = new DockerImage(gitCloneCommand, githubApiUser, githubApiToken, slugBuilderVersion)
  }
}
