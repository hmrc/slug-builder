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

package uk.gov.hmrc.slugbuilder.tools

import java.nio.file.{Files, Paths}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import org.scalacheck.Gen
import org.scalamock.scalatest.MockFactory
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.ws.StandaloneWSClient
import uk.gov.hmrc.slugbuilder.TestWSRequest
import uk.gov.hmrc.slugbuilder.generators.Generators.Implicits._
import uk.gov.hmrc.slugbuilder.generators.Generators._

import scala.concurrent.Future
import scala.language.postfixOps

class FileDownloaderSpec extends WordSpec with MockFactory with ScalaFutures {

  "download" should {

    "return Right if file can be downloaded from the given url" in new Setup {
      (wsRequest.get _)
        .expects()
        .returning(Future.successful(wsResponse))

      (wsResponse.status _)
        .expects()
        .returning(200)

      val fileContent = ByteString("some content")
      (wsResponse.bodyAsSource _)
        .expects()
        .returning(Source.single(fileContent))

      fileDownloader.download(fileUrl, destinationFileName).value.futureValue should be('right)

      val pathToDownloadedFile = Paths.get(destinationFileName.toString)
      pathToDownloadedFile.toFile.deleteOnExit()

      Files.exists(pathToDownloadedFile) shouldBe true

      FileIO
        .fromPath(pathToDownloadedFile)
        .runWith(Sink.fold(ByteString.empty)((content, line) => content concat line))
        .futureValue shouldBe fileContent
    }

    "return Left if there was an error on downloading the file" in new Setup {
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

      fileDownloader.download(fileUrl, destinationFileName).value.futureValue shouldBe
        Left(DownloadError(downloadingException.getMessage))

      Paths.get(destinationFileName.toString).toFile.deleteOnExit()
    }

    "return Left if file does not exist" in new Setup {
      (wsRequest.get _)
        .expects()
        .returning(Future.successful(wsResponse))

      (wsResponse.status _)
        .expects()
        .returning(404)

      fileDownloader.download(fileUrl, destinationFileName).value.futureValue shouldBe
        Left(DownloadError("A file does not exist"))
    }

    "return Left when got unexpected status from fetching the file" in {
      allHttpStatusCodes filterNot Seq(200, 404).contains foreach { status =>
        new Setup {
          (wsRequest.get _)
            .expects()
            .returning(Future.successful(wsResponse))

          (wsResponse.status _)
            .expects()
            .returning(status)

          fileDownloader.download(fileUrl, destinationFileName).value.futureValue shouldBe
            Left(DownloadError(s"Returned status $status"))
        }
      }
    }

    "return Left if fetching the file results in an exception" in new Setup {
      val exception = new Exception("some error")
      (wsRequest.get _)
        .expects()
        .returning(Future.failed(exception))

      fileDownloader.download(fileUrl, destinationFileName).value.futureValue shouldBe
        Left(DownloadError(exception.getMessage))
    }
  }

  private trait Setup {
    val fileUrl = FileUrl({
      for {
        numberOfPathElements <- Gen.choose(1, 5)
        pathElements         <- Gen.listOfN(numberOfPathElements, nonEmptyStrings)
        hostName             <- nonEmptyStrings
        fileName             <- nonEmptyStrings map (_ + ".tgz")
      } yield (s"http://$hostName" +: pathElements +: fileName +: Nil).mkString("/")
    }.generateOne)

    val destinationFileName = DestinationFileName(nonEmptyStrings.generateOne)

    implicit val system: ActorSystem    = ActorSystem()
    implicit val mat: ActorMaterializer = ActorMaterializer()
    val wsClient                        = mock[StandaloneWSClient]
    val wsRequest                       = mock[TestWSRequest]
    val wsResponse                      = mock[wsRequest.Response]

    val fileDownloader = new FileDownloader(wsClient)

    (wsClient
      .url(_: String))
      .expects(fileUrl.toString)
      .returning(wsRequest)
  }
}
