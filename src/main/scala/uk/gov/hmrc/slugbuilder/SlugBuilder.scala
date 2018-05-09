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

import cats.implicits._
import play.api.LoggerLike

class SlugBuilder(slugChecker: SlugChecker, artifactChecker: ArtifactChecker, progressReporter: ProgressReporter) {

  def create(repoName: String, releaseVersion: String): Unit =
    createSlug(RepositoryName(repoName), ReleaseVersion(releaseVersion)) leftMap (message =>
      throw new RuntimeException(message))

  // format: off
  private def createSlug(repositoryName: RepositoryName, version: ReleaseVersion) =
    for {
      slugDoesNotExist      <- slugChecker.checkIfDoesNotExist(repositoryName, version)
      _                     = progressReporter.show(slugDoesNotExist)
      artifactExistsMessage <- artifactChecker.checkIfExists(repositoryName, version)
      _                     = progressReporter.show(artifactExistsMessage)
    } yield ()
  // format: on
}
