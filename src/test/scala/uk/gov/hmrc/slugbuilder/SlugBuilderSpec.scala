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

    "finish successfully if " +
      "slug for the given version of the microservice does not exist and " +
      "microservice artifact can be downloaded and " +
      "app-config-base can be downloaded and " +
      "a slug file is assembled" in new Setup {
      forAll(repositoryNameGen, releaseVersionGen) { (repositoryName, releaseVersion) =>
        (slugChecker
          .checkIfDoesNotExist(_: RepositoryName, _: ReleaseVersion))
          .expects(repositoryName, releaseVersion)
          .returning(rightT[Future, String]("Slug does not exist"))

        (progressReporter.printSuccess(_: String)).expects("Slug does not exist")

        (artifactFetcher
          .download(_: RepositoryName, _: ReleaseVersion))
          .expects(repositoryName, releaseVersion)
          .returning(rightT[Future, String]("Artifact downloaded"))

        (progressReporter.printSuccess(_: String)).expects("Artifact downloaded")

        (appConfigBaseFetcher
          .download(_: RepositoryName))
          .expects(repositoryName)
          .returning(rightT[Future, String]("app-config-base downloaded"))

        (progressReporter.printSuccess(_: String)).expects("app-config-base downloaded")

        (slugFileAssembler
          .assemble(_: RepositoryName, _: ReleaseVersion))
          .expects(repositoryName, releaseVersion)
          .returning(rightT[Future, String]("Slug assembled"))

        (progressReporter.printSuccess(_: String)).expects("Slug assembled")

        slugBuilder.create(repositoryName, releaseVersion).value.futureValue should be('right)
      }
    }

    "not create the slug if it already exists in the Webstore" in new Setup {
      val repositoryName = repositoryNameGen.generateOne
      val releaseVersion = releaseVersionGen.generateOne

      (slugChecker
        .checkIfDoesNotExist(_: RepositoryName, _: ReleaseVersion))
        .expects(repositoryName, releaseVersion)
        .returning(leftT[Future, String]("Slug does exist"))

      (progressReporter.printError(_: String)).expects("Slug does exist")

      slugBuilder.create(repositoryName, releaseVersion).value.futureValue should be('left)
    }

    "not create the slug if there is no artifact in the Artifactory" in new Setup {
      val repositoryName = repositoryNameGen.generateOne
      val releaseVersion = releaseVersionGen.generateOne

      (slugChecker
        .checkIfDoesNotExist(_: RepositoryName, _: ReleaseVersion))
        .expects(repositoryName, releaseVersion)
        .returning(rightT[Future, String]("Slug does not exist"))

      (progressReporter.printSuccess(_: String)).expects("Slug does not exist")

      (artifactFetcher
        .download(_: RepositoryName, _: ReleaseVersion))
        .expects(repositoryName, releaseVersion)
        .returning(leftT[Future, String]("Artifact does not exist"))

      (progressReporter.printError(_: String)).expects("Artifact does not exist")

      slugBuilder.create(repositoryName, releaseVersion).value.futureValue should be('left)
    }

    "not create the slug if there is app-config-base in the Webstore" in new Setup {
      val repositoryName = repositoryNameGen.generateOne
      val releaseVersion = releaseVersionGen.generateOne

      (slugChecker
        .checkIfDoesNotExist(_: RepositoryName, _: ReleaseVersion))
        .expects(repositoryName, releaseVersion)
        .returning(rightT[Future, String]("Slug does not exist"))

      (progressReporter.printSuccess(_: String)).expects("Slug does not exist")

      (artifactFetcher
        .download(_: RepositoryName, _: ReleaseVersion))
        .expects(repositoryName, releaseVersion)
        .returning(rightT[Future, String]("Artifact downloaded"))

      (progressReporter.printSuccess(_: String)).expects("Artifact downloaded")

      (appConfigBaseFetcher
        .download(_: RepositoryName))
        .expects(repositoryName)
        .returning(leftT[Future, String]("app-config-base does not exist"))

      (progressReporter.printError(_: String)).expects("app-config-base does not exist")

      slugBuilder.create(repositoryName, releaseVersion).value.futureValue should be('left)
    }

    "not create the slug if slug assembly step failed" in new Setup {
      val repositoryName = repositoryNameGen.generateOne
      val releaseVersion = releaseVersionGen.generateOne

      (slugChecker
        .checkIfDoesNotExist(_: RepositoryName, _: ReleaseVersion))
        .expects(repositoryName, releaseVersion)
        .returning(rightT[Future, String]("Slug does not exist"))

      (progressReporter.printSuccess(_: String)).expects("Slug does not exist")

      (artifactFetcher
        .download(_: RepositoryName, _: ReleaseVersion))
        .expects(repositoryName, releaseVersion)
        .returning(rightT[Future, String]("Artifact downloaded"))

      (progressReporter.printSuccess(_: String)).expects("Artifact downloaded")

      (appConfigBaseFetcher
        .download(_: RepositoryName))
        .expects(repositoryName)
        .returning(rightT[Future, String]("app-config-base downloaded"))

      (progressReporter.printSuccess(_: String)).expects("app-config-base downloaded")

      (slugFileAssembler
        .assemble(_: RepositoryName, _: ReleaseVersion))
        .expects(repositoryName, releaseVersion)
        .returning(leftT[Future, String]("Slug file assembly failure"))

      (progressReporter.printError(_: String)).expects("Slug file assembly failure")

      slugBuilder.create(repositoryName, releaseVersion).value.futureValue should be('left)
    }
  }

  private trait Setup {
    val progressReporter     = mock[ProgressReporter]
    val slugChecker          = mock[SlugChecker]
    val artifactFetcher      = mock[ArtifactFetcher]
    val appConfigBaseFetcher = mock[AppConfigBaseFetcher]
    val slugFileAssembler    = mock[SlugFileAssembler]

    val slugBuilder = new SlugBuilder(
      progressReporter,
      slugChecker,
      artifactFetcher,
      appConfigBaseFetcher,
      slugFileAssembler
    )
  }
}
