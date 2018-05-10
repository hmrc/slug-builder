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

import java.nio.file.{Files, Paths}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.ws.{StandaloneWSClient, StandaloneWSRequest, StandaloneWSResponse}
import uk.gov.hmrc.slugbuilder.generators.Generators.Implicits._
import uk.gov.hmrc.slugbuilder.generators.Generators._

import scala.concurrent.Future

class ArtifactFetcherSpec extends WordSpec with Matchers with MockFactory with ScalaFutures {

  "download" should {

    "return Right if artifact can be downloaded from Artifactory successfully" in new Setup {
      (wsRequest.get _)
        .expects()
        .returning(Future.successful(wsResponse))

      (wsResponse.status _)
        .expects()
        .returning(200)

      val artifactContent = ByteString("some content")
      (wsResponse.bodyAsSource _)
        .expects()
        .returning(Source.single(artifactContent))

      artifactFetcher.download(repositoryName, releaseVersion).value.futureValue shouldBe
        Right(s"Artifact successfully downloaded from $url")

      val pathToDownloadedFile = Paths.get(s"$repositoryName-$releaseVersion.tgz")
      pathToDownloadedFile.toFile.deleteOnExit()

      Files.exists(pathToDownloadedFile) shouldBe true

      FileIO
        .fromPath(pathToDownloadedFile)
        .runWith(Sink.fold(ByteString.empty)((content, line) => content concat line))
        .futureValue shouldBe artifactContent
    }

    "return Left if artifact cannot be downloaded from Artifactory successfully" in new Setup {
      (wsRequest.get _)
        .expects()
        .returning(Future.successful(wsResponse))

      (wsResponse.status _)
        .expects()
        .returning(200)

      val downloadingException = new Exception("downloading problem")
      (wsResponse.bodyAsSource _)
        .expects()
        .returning(Source.failed(downloadingException))

      artifactFetcher.download(repositoryName, releaseVersion).value.futureValue shouldBe
        Left(s"Artifact couldn't be downloaded from $url. Cause: ${downloadingException.getMessage}")

      Paths.get(s"$repositoryName-$releaseVersion.tgz").toFile.deleteOnExit()
    }

    "return Left if artifact does not exist" in new Setup {
      (wsRequest.get _)
        .expects()
        .returning(Future.successful(wsResponse))

      (wsResponse.status _)
        .expects()
        .returning(404)

      artifactFetcher.download(repositoryName, releaseVersion).value.futureValue shouldBe
        Left(s"Artifact does not exist at: $url")
    }

    201 +: 400 +: 500 +: Nil foreach { status =>
      s"return Left when got $status status from fetching artifact" in new Setup {
        (wsRequest.get _)
          .expects()
          .returning(Future.successful(wsResponse))

        (wsResponse.status _)
          .expects()
          .returning(status)

        artifactFetcher.download(repositoryName, releaseVersion).value.futureValue shouldBe
          Left(s"Cannot fetch artifact from $url. Returned status $status")
      }
    }

    "return Left if fetching artifact results in an exception" in new Setup {
      val exception = new Exception("some error")
      (wsRequest.get _)
        .expects()
        .returning(Future.failed(exception))

      artifactFetcher.download(repositoryName, releaseVersion).value.futureValue shouldBe
        Left(s"Cannot fetch artifact from $url. Got exception: ${exception.getMessage}")
    }
  }

  private trait Setup {
    val artifactoryUri = "artifactoryUri"
    val repositoryName = repositoryNameGen.generateOne
    val releaseVersion = releaseVersionGen.generateOne
    val url =
      s"$artifactoryUri/uk/gov/hmrc/${repositoryName}_2.11/$releaseVersion/${repositoryName}_2.11-$releaseVersion.tgz"

    implicit val system: ActorSystem    = ActorSystem()
    implicit val mat: ActorMaterializer = ActorMaterializer()
    val wsClient                        = mock[StandaloneWSClient]
    val wsRequest                       = mock[TestWSRequest]
    val wsResponse                      = mock[wsRequest.Response]

    val artifactFetcher = new ArtifactFetcher(wsClient, artifactoryUri)

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
