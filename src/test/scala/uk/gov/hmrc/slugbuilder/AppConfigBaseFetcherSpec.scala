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
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.ws.StandaloneWSClient
import uk.gov.hmrc.slugbuilder.generators.Generators.Implicits._
import uk.gov.hmrc.slugbuilder.generators.Generators._

import scala.concurrent.Future

class AppConfigBaseFetcherSpec extends WordSpec with MockFactory with ScalaFutures {

  "download" should {

    "return Right if service's app-config-base can be downloaded from Webstore successfully" in new Setup {
      (wsRequest.get _)
        .expects()
        .returning(Future.successful(wsResponse))

      (wsResponse.status _)
        .expects()
        .returning(200)

      val appConfigBaseContent = ByteString("some content")
      (wsResponse.bodyAsSource _)
        .expects()
        .returning(Source.single(appConfigBaseContent))

      appConfigBaseFetcher.download(repositoryName).value.futureValue shouldBe
        Right(s"app-config-base successfully downloaded from $url")

      val pathToDownloadedFile = Paths.get(s"$repositoryName.conf")
      pathToDownloadedFile.toFile.deleteOnExit()

      Files.exists(pathToDownloadedFile) shouldBe true

      FileIO
        .fromPath(pathToDownloadedFile)
        .runWith(Sink.fold(ByteString.empty)((content, line) => content concat line))
        .futureValue shouldBe appConfigBaseContent
    }

    "return Left if app-config-base cannot be downloaded from Webstore" in new Setup {
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

      appConfigBaseFetcher.download(repositoryName).value.futureValue shouldBe
        Left(s"app-config-base couldn't be downloaded from $url. Cause: ${downloadingException.getMessage}")

      Paths.get(s"$repositoryName.conf").toFile.deleteOnExit()
    }

    "return Left if app-config-base does not exist" in new Setup {
      (wsRequest.get _)
        .expects()
        .returning(Future.successful(wsResponse))

      (wsResponse.status _)
        .expects()
        .returning(404)

      appConfigBaseFetcher.download(repositoryName).value.futureValue shouldBe
        Left(s"app-config-base does not exist at: $url")
    }

    "return Left when got unexpected status from fetching app-config-base" in {
      allHttpStatusCodes filterNot Seq(200, 404).contains foreach { status =>
        new Setup {
          (wsRequest.get _)
            .expects()
            .returning(Future.successful(wsResponse))

          (wsResponse.status _)
            .expects()
            .returning(status)

          appConfigBaseFetcher.download(repositoryName).value.futureValue shouldBe
            Left(s"Cannot fetch app-config-base from $url. Returned status $status")
        }
      }
    }

    "return Left if fetching app-config-base results in an exception" in new Setup {
      val exception = new Exception("some error")
      (wsRequest.get _)
        .expects()
        .returning(Future.failed(exception))

      appConfigBaseFetcher.download(repositoryName).value.futureValue shouldBe
        Left(s"Cannot fetch app-config-base from $url. Got exception: ${exception.getMessage}")
    }
  }

  private trait Setup {
    val webstoreUri    = "webstoreUri"
    val repositoryName = repositoryNameGen.generateOne
    val url            = s"$webstoreUri/app-config-base/$repositoryName.conf"

    implicit val system: ActorSystem    = ActorSystem()
    implicit val mat: ActorMaterializer = ActorMaterializer()
    val wsClient                        = mock[StandaloneWSClient]
    val wsRequest                       = mock[TestWSRequest]
    val wsResponse                      = mock[wsRequest.Response]

    val appConfigBaseFetcher = new AppConfigBaseFetcher(wsClient, webstoreUri)

    (wsClient
      .url(_: String))
      .expects(url)
      .returning(wsRequest)
  }
}
