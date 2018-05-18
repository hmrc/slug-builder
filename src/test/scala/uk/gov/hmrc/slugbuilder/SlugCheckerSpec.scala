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
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.ws.StandaloneWSClient
import uk.gov.hmrc.slugbuilder.functions.SlugArtifactName
import uk.gov.hmrc.slugbuilder.generators.Generators.Implicits._
import uk.gov.hmrc.slugbuilder.generators.Generators._

import scala.concurrent.Future

class SlugCheckerSpec extends WordSpec with MockFactory with ScalaFutures {

  "verifySlugNotCreatedYet" should {

    "return Left if slug already exists" in new Setup {
      (wsRequest.head _)
        .expects()
        .returning(Future.successful(wsResponse))

      (wsResponse.status _)
        .expects()
        .returning(200)

      slugChecker.verifySlugNotCreatedYet(repositoryName, releaseVersion).value.futureValue shouldBe
        Left(s"Slug already exists at: $url")
    }

    "return Right if slug does not exist" in new Setup {
      (wsRequest.head _)
        .expects()
        .returning(Future.successful(wsResponse))

      (wsResponse.status _)
        .expects()
        .returning(404)

      slugChecker.verifySlugNotCreatedYet(repositoryName, releaseVersion).value.futureValue shouldBe
        Right(s"No slug created yet at $url")
    }

    "return Left when got unexpected status from checking if slug exists" in {
      allHttpStatusCodes filterNot Seq(200, 404).contains foreach { status =>
        new Setup {
          (wsRequest.head _)
            .expects()
            .returning(Future.successful(wsResponse))

          (wsResponse.status _)
            .expects()
            .returning(status)

          slugChecker.verifySlugNotCreatedYet(repositoryName, releaseVersion).value.futureValue shouldBe
            Left(s"Cannot check if slug exists at $url. Returned status $status")
        }
      }
    }

    "return Left if calling webstore results in an exception" in new Setup {
      val exception = new Exception("some error")
      (wsRequest.head _)
        .expects()
        .returning(Future.failed(exception))

      slugChecker.verifySlugNotCreatedYet(repositoryName, releaseVersion).value.futureValue shouldBe
        Left(s"Cannot check if slug exists at $url. Got exception: ${exception.getMessage}")
    }
  }

  private trait Setup {
    private val webstoreUri      = "webstoreUri"
    private val artifactName     = nonEmptyStrings.generateOne
    private val wsClient         = mock[StandaloneWSClient]
    private val slugArtifactName = mock[SlugArtifactName]
    val repositoryName           = repositoryNameGen.generateOne
    val releaseVersion           = releaseVersionGen.generateOne

    val slugChecker = new SlugChecker(wsClient, webstoreUri, slugArtifactName)

    val url        = s"$webstoreUri/slugs/$repositoryName/$artifactName"
    val wsRequest  = mock[TestWSRequest]
    val wsResponse = mock[wsRequest.Response]

    (slugArtifactName.apply(_: RepositoryName, _: ReleaseVersion))
      .expects(repositoryName, releaseVersion)
      .returning(artifactName)

    (wsClient
      .url(_: String))
      .expects(url)
      .returning(wsRequest)
  }
}
