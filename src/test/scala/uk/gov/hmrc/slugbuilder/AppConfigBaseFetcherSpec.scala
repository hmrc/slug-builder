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
import uk.gov.hmrc.slugbuilder.functions.AppConfigBaseFileName
import uk.gov.hmrc.slugbuilder.generators.Generators.Implicits._
import uk.gov.hmrc.slugbuilder.generators.Generators._
import uk.gov.hmrc.slugbuilder.tools.{DestinationFileName, DownloadError, FileDownloader, FileUrl}

class AppConfigBaseFetcherSpec extends WordSpec with MockFactory {

  "download" should {

    "return Right if service's app-config-base can be downloaded from Webstore successfully" in new Setup {
      (fileDownloader
        .download(_: FileUrl, _: DestinationFileName))
        .expects(fileUrl, destinationFileName)
        .returning(Right())

      appConfigBaseFetcher
        .download(repositoryName) shouldBe Right(s"app-config-base successfully downloaded from $fileUrl")
    }

    "return Left if there was an error when downloading app-config-base from Webstore" in new Setup {
      val downloadingProblem = DownloadError("downloading problem")
      (fileDownloader
        .download(_: FileUrl, _: DestinationFileName))
        .expects(fileUrl, destinationFileName)
        .returning(Left(downloadingProblem))

      appConfigBaseFetcher.download(repositoryName) shouldBe
        Left(s"app-config-base couldn't be downloaded from $fileUrl. Cause: $downloadingProblem")
    }
  }

  private trait Setup {
    val repositoryName      = repositoryNameGen.generateOne
    val webstoreUri         = "webstoreUri"
    val fileUrl             = FileUrl(s"$webstoreUri/app-config-base/$repositoryName.conf")
    val destinationFileName = DestinationFileName(AppConfigBaseFileName(repositoryName))

    val fileDownloader       = mock[FileDownloader]
    val appConfigBaseFetcher = new AppConfigBaseFetcher(fileDownloader, webstoreUri)
  }
}
