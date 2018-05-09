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

import org.scalamock.scalatest.MockFactory
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.prop.PropertyChecks
import uk.gov.hmrc.slugbuilder.generators.Generators.Implicits._
import uk.gov.hmrc.slugbuilder.generators.Generators._

class SlugBuilderSpec extends WordSpec with PropertyChecks with MockFactory {

  "create" should {

    "check if the slug does not exist" in new Setup {
      forAll(nonEmptyStrings, releaseVersions) { (repoName, releaseVersion) =>
        (slugChecker
          .checkIfDoesNotExist(_: RepositoryName, _: ReleaseVersion))
          .expects(RepositoryName(repoName), ReleaseVersion(releaseVersion))
          .returning(Right("Slug does not exist"))

        (progressReporter.show(_: String)).expects("Slug does not exist")

        (artifactChecker
          .checkIfExists(_: RepositoryName, _: ReleaseVersion))
          .expects(RepositoryName(repoName), ReleaseVersion(releaseVersion))
          .returning(Right("Artifact exists"))

        (progressReporter.show(_: String)).expects("Artifact exists")

        slugBuilder.create(repoName, releaseVersion)
      }
    }

    "stop slug creation if slug exists already" in new Setup {
      val repoName       = nonEmptyStrings.generateOne
      val releaseVersion = releaseVersions.generateOne

      (slugChecker
        .checkIfDoesNotExist(_: RepositoryName, _: ReleaseVersion))
        .expects(RepositoryName(repoName), ReleaseVersion(releaseVersion))
        .returning(Left("Slug does exist"))

      an[Exception] should be thrownBy slugBuilder.create(repoName, releaseVersion)
    }

    "stop slug creation if artifact does not exist" in new Setup {
      val repoName       = nonEmptyStrings.generateOne
      val releaseVersion = releaseVersions.generateOne

      (slugChecker
        .checkIfDoesNotExist(_: RepositoryName, _: ReleaseVersion))
        .expects(RepositoryName(repoName), ReleaseVersion(releaseVersion))
        .returning(Right("Slug does not exist"))

      (progressReporter.show(_: String)).expects("Slug does not exist")

      (artifactChecker
        .checkIfExists(_: RepositoryName, _: ReleaseVersion))
        .expects(RepositoryName(repoName), ReleaseVersion(releaseVersion))
        .returning(Left("Artifact does not exist"))

      an[Exception] should be thrownBy slugBuilder.create(repoName, releaseVersion)
    }

    "throw an exception if repository name is blank" in new Setup {
      intercept[IllegalArgumentException](slugBuilder.create(" ", releaseVersions.generateOne)).getMessage shouldBe "Blank repository name not allowed"
    }

    "throw an exception if release version is blank" in new Setup {
      intercept[IllegalArgumentException](slugBuilder.create(nonEmptyStrings.generateOne, " ")).getMessage shouldBe "Blank release version not allowed"
    }
  }

  private trait Setup {
    val slugChecker      = mock[SlugChecker]
    val artifactChecker  = mock[ArtifactChecker]
    val progressReporter = mock[ProgressReporter]

    val slugBuilder = new SlugBuilder(slugChecker, artifactChecker, progressReporter)
  }
}
