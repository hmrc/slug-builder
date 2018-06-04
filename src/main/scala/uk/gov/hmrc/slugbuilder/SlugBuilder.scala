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

class SlugBuilder(
  progressReporter: ProgressReporter,
  slugChecker: SlugChecker,
  artifactFetcher: ArtifactFetcher,
  appConfigBaseFetcher: AppConfigBaseFetcher,
  slugFileAssembler: SlugFileAssembler) {

  import progressReporter._
  import slugChecker._

  def create(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): EitherT[Future, Unit, Unit] = {
    for {
      _ <- verifySlugNotCreatedYet(repositoryName, releaseVersion) map printSuccess
      _ <- artifactFetcher.download(repositoryName, releaseVersion) map printSuccess
      _ <- appConfigBaseFetcher.download(repositoryName) map printSuccess
      _ <- slugFileAssembler.assemble(repositoryName, releaseVersion) map printSuccess
    } yield ()
  }.leftMap(printError)
}
