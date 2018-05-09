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
import play.api.libs.ws.{StandaloneWSClient, StandaloneWSRequest, StandaloneWSResponse}
import uk.gov.hmrc.slugbuilder.generators.Generators.Implicits._
import uk.gov.hmrc.slugbuilder.generators.Generators._

import scala.concurrent.Future

class SlugCheckerSpec extends WordSpec with MockFactory with ScalaFutures {

  "checkIfDoesNotExist" should {

    "return Left if slug already exists" in new Setup {
      (wsRequest.get _)
        .expects()
        .returning(Future.successful(wsResponse))

      (wsResponse.status _)
        .expects()
        .returning(200)

      slugChecker.checkIfDoesNotExist(repositoryName, releaseVersion).value.futureValue shouldBe
        Left(s"Slug already exists at: $url")
    }

    "return Right if slug does not exist" in new Setup {
      (wsRequest.get _)
        .expects()
        .returning(Future.successful(wsResponse))

      (wsResponse.status _)
        .expects()
        .returning(404)

      slugChecker.checkIfDoesNotExist(repositoryName, releaseVersion).value.futureValue shouldBe
        Right("Slug does not exist")
    }

    201 +: 400 +: 500 +: Nil foreach { status =>
      s"return Left when got $status status from checking if slug exists" in new Setup {
        (wsRequest.get _)
          .expects()
          .returning(Future.successful(wsResponse))

        (wsResponse.status _)
          .expects()
          .returning(status)

        slugChecker.checkIfDoesNotExist(repositoryName, releaseVersion).value.futureValue shouldBe
          Left(s"Cannot check if slug exists at $url. Returned status $status")
      }
    }

    "return Left if calling webstore results in an exception" in new Setup {
      val exception = new Exception("some error")
      (wsRequest.get _)
        .expects()
        .returning(Future.failed(exception))

      slugChecker.checkIfDoesNotExist(repositoryName, releaseVersion).value.futureValue shouldBe
        Left(s"Cannot check if slug exists at $url. Got exception: ${exception.getMessage}")
    }
  }

  private trait Setup {
    val webstoreUri        = "webstoreUri"
    val slugBuilderVersion = "0.5.2"
    val repositoryName     = repositoryNameGen.generateOne
    val releaseVersion     = releaseVersionGen.generateOne
    val wsClient           = mock[StandaloneWSClient]

    val slugChecker = new SlugChecker(wsClient, webstoreUri, slugBuilderVersion)

    val url        = s"$webstoreUri/slugs/$repositoryName/${repositoryName}_${releaseVersion}_$slugBuilderVersion.tgz"
    val wsRequest  = mock[TestWSRequest]
    val wsResponse = mock[wsRequest.Response]

    (wsClient
      .url(_: String))
      .expects(url)
      .returning(wsRequest)
  }

  private trait TestWSRequest extends StandaloneWSRequest {
    override type Self     = TestWSRequest
    override type Response = StandaloneWSResponse
  }
}
