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

import cats.data.EitherT._
import cats.implicits._
import org.scalamock.scalatest.MockFactory
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.PropertyChecks
import uk.gov.hmrc.slugbuilder.generators.Generators.Implicits._
import uk.gov.hmrc.slugbuilder.generators.Generators._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SlugBuilderSpec extends WordSpec with PropertyChecks with MockFactory with ScalaFutures {

  "create" should {

    "check if the slug does not exist" in new Setup {
      forAll(nonEmptyStrings, releaseVersions) { (repoName, releaseVersion) =>
        (slugChecker
          .checkIfDoesNotExist(_: RepositoryName, _: ReleaseVersion))
          .expects(RepositoryName(repoName), ReleaseVersion(releaseVersion))
          .returning(rightT[Future, String]("Slug does not exist"))

        (progressReporter.printSuccess(_: String)).expects("Slug does not exist")

        (artifactFetcher
          .fetch(_: RepositoryName, _: ReleaseVersion))
          .expects(RepositoryName(repoName), ReleaseVersion(releaseVersion))
          .returning(rightT[Future, String]("Artifact exists"))

        (progressReporter.printSuccess(_: String)).expects("Artifact exists")

        slugBuilder.create(repoName, releaseVersion).value.futureValue should be('right)
      }
    }

    "stop slug creation if slug exists already" in new Setup {
      val repoName       = nonEmptyStrings.generateOne
      val releaseVersion = releaseVersions.generateOne

      (slugChecker
        .checkIfDoesNotExist(_: RepositoryName, _: ReleaseVersion))
        .expects(RepositoryName(repoName), ReleaseVersion(releaseVersion))
        .returning(leftT[Future, String]("Slug does exist"))

      (progressReporter.printError(_: String)).expects("Slug does exist")

      slugBuilder.create(repoName, releaseVersion).value.futureValue should be('left)
    }

    "stop slug creation if artifact does not exist" in new Setup {
      val repoName       = nonEmptyStrings.generateOne
      val releaseVersion = releaseVersions.generateOne

      (slugChecker
        .checkIfDoesNotExist(_: RepositoryName, _: ReleaseVersion))
        .expects(RepositoryName(repoName), ReleaseVersion(releaseVersion))
        .returning(rightT[Future, String]("Slug does not exist"))

      (progressReporter.printSuccess(_: String)).expects("Slug does not exist")

      (artifactFetcher
        .fetch(_: RepositoryName, _: ReleaseVersion))
        .expects(RepositoryName(repoName), ReleaseVersion(releaseVersion))
        .returning(leftT[Future, String]("Artifact does not exist"))

      (progressReporter.printError(_: String)).expects("Artifact does not exist")

      slugBuilder.create(repoName, releaseVersion).value.futureValue should be('left)
    }

    "return left if repository name is blank" in new Setup {

      (progressReporter.printError(_: String)).expects("Blank repository name not allowed")

      slugBuilder.create(" ", releaseVersions.generateOne).value.futureValue should be('left)
    }

    "return left if release version is blank" in new Setup {
      (progressReporter.printError(_: String)).expects("Blank release version not allowed")
      slugBuilder.create(nonEmptyStrings.generateOne, " ").value.futureValue should be('left)
    }
  }

  private trait Setup {
    val slugChecker      = mock[SlugChecker]
    val artifactFetcher  = mock[ArtifactFetcher]
    val progressReporter = mock[ProgressReporter]

    val slugBuilder = new SlugBuilder(slugChecker, artifactFetcher, progressReporter)
  }
}
