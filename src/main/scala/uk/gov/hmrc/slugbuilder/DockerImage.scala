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

import cats.data.EitherT
import cats.implicits._
import org.eclipse.jgit.api.CloneCommand
import uk.gov.hmrc.slugbuilder.tools.CommandExecutor.perform

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DockerImage(cloneCommand: CloneCommand, githubApiUser: String, githubApiToken: String, slugBuilderVersion: String) {

  def create(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): EitherT[Future, String, String] =
    EitherT
      .fromEither[Future] {
        for {
          _ <- cloneBuildPack
        } yield ()
      }
      .map(_ => s"Docker image ${repositoryName}_${releaseVersion}_$slugBuilderVersion.tgz created")

  private def cloneBuildPack = {
    val repoUrl = "github.com/hmrc/buildpack-java-jar.git"
    perform(
      cloneCommand
        .setURI(s"https://$githubApiUser:$githubApiToken@$repoUrl")
        .setDirectory(Paths.get("bp").toFile)
        .call()
    ) leftMap (exception => s"Couldn't clone $repoUrl. Cause: ${exception.getMessage}")
  }
}
