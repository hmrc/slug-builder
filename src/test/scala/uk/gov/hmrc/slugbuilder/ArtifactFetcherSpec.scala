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
import org.scalamock.scalatest.MockFactory
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.slugbuilder.functions.ArtifactFileName
import uk.gov.hmrc.slugbuilder.generators.Generators.Implicits._
import uk.gov.hmrc.slugbuilder.generators.Generators._
import uk.gov.hmrc.slugbuilder.tools.{DestinationFileName, DownloadError, FileDownloader, FileUrl}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ArtifactFetcherSpec extends WordSpec with MockFactory with ScalaFutures {

  "download" should {

    "return Right if artifact can be downloaded from Artifactory successfully" in new Setup {
      (fileDownloader.download(_: FileUrl, _: DestinationFileName))
        .expects(fileUrl, destinationFileName)
        .returning(EitherT.rightT[Future, DownloadError](()))

      artifactFetcher.download(repositoryName, releaseVersion).value.futureValue shouldBe
        Right(s"Artifact successfully downloaded from $fileUrl")
    }

    "return Left if there was an error when downloading the artifact from Artifactory" in new Setup {
      val downloadingProblem = DownloadError("downloading problem")
      (fileDownloader.download(_: FileUrl, _: DestinationFileName))
        .expects(fileUrl, destinationFileName)
        .returning(EitherT.leftT[Future, Unit](downloadingProblem))

      artifactFetcher.download(repositoryName, releaseVersion).value.futureValue shouldBe
        Left(s"Artifact couldn't be downloaded from $fileUrl. Cause: $downloadingProblem")
    }
  }

  private trait Setup {
    val repositoryName = repositoryNameGen.generateOne
    val releaseVersion = releaseVersionGen.generateOne
    val artifactoryUri = "artifactoryUri"
    val fileUrl = FileUrl(
      s"$artifactoryUri/uk/gov/hmrc/${repositoryName}_2.11/$releaseVersion/${repositoryName}_2.11-$releaseVersion.tgz"
    )
    val destinationFileName = DestinationFileName(ArtifactFileName(repositoryName, releaseVersion))

    val fileDownloader                        = mock[FileDownloader]
    val artifactFetcher = new ArtifactFetcher(fileDownloader, artifactoryUri)
  }
}
