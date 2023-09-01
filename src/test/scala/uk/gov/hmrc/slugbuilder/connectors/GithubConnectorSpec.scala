/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.slugbuilder.connectors

import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, EitherValues}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.slugbuilder.{AppConfigBaseFileName, TestWSRequest}
import uk.gov.hmrc.slugbuilder.generators.Generators.Implicits._
import uk.gov.hmrc.slugbuilder.generators.Generators.{repositoryNameGen}

class GithubConnectorSpec
  extends AnyWordSpec
     with MockFactory
     with ScalaFutures
     with Matchers
     with BeforeAndAfterAll
     with EitherValues {

  "download From Webstore" should {
    "return Right if service's app-config-base can be downloaded from Webstore successfully" in new Setup {
      val fileUrl             = FileUrl(s"https://raw.githubusercontent.com/hmrc/app-config-base/main/$repositoryName.conf")
      val destinationFileName = DestinationFileName(AppConfigBaseFileName(repositoryName).toString)
      val headers             = Map("Authorization" -> s"Token $githubApiToken")

      (fileDownloader.download _)
        .expects(fileUrl, destinationFileName, headers)
        .returning(Right(()))

      connector
        .downloadAppConfigBase(repositoryName) shouldBe Right(s"Successfully downloaded app-config-base from $fileUrl")
    }

    "return Left if there was an error when downloading app-config-base from Webstore" in new Setup {
      val fileUrl             = FileUrl(s"https://raw.githubusercontent.com/hmrc/app-config-base/main/$repositoryName.conf")
      val destinationFileName = DestinationFileName(AppConfigBaseFileName(repositoryName).toString)
      val headers             = Map("Authorization" -> s"Token $githubApiToken")

      val downloadingProblem = DownloadError("downloading problem")
      (fileDownloader.download _)
        .expects(fileUrl, destinationFileName, headers)
        .returning(Left(downloadingProblem))

      connector.downloadAppConfigBase(repositoryName) shouldBe
        Left(s"app-config-base couldn't be downloaded from $fileUrl. Cause: $downloadingProblem")
    }
  }

  private trait Setup {
    val githubApiToken       = "token"
    val repositoryName       = repositoryNameGen.generateOne

    val fileDownloader = mock[FileDownloader]
    val connector = new GithubConnector(
      fileDownloader,
      githubApiToken
    )

    val wsRequest  = mock[TestWSRequest]
    val wsResponse = mock[wsRequest.Response]
  }
}
