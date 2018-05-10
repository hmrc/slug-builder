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

import cats.data.EitherT
import cats.implicits._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SlugBuilder(slugChecker: SlugChecker, artifactFetcher: ArtifactFetcher, progressReporter: ProgressReporter) {

  def create(repoName: String, releaseVersion: String): EitherT[Future, Unit, Unit] =
    createSlug(repoName, releaseVersion)
      .leftMap(progressReporter.printError)

  // format: off
  private def createSlug(repoName: String, releaseVersion: String) =
    for {
      repositoryName           <- EitherT.fromEither[Future](RepositoryName.create(repoName))
      version                  <- EitherT.fromEither[Future](ReleaseVersion.create(releaseVersion))

      slugDoesNotExist         <- slugChecker.checkIfDoesNotExist(repositoryName, version)
      _                        = progressReporter.printSuccess(slugDoesNotExist)

      artifactDownloadMessage  <- artifactFetcher.download(repositoryName, version)
      _                        = progressReporter.printSuccess(artifactDownloadMessage)
    } yield ()
  // format: on
}
