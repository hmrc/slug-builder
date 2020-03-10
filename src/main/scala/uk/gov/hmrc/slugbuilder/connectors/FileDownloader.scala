/*
 * Copyright 2020 HM Revenue & Customs
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

import java.nio.file.Paths
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import play.api.libs.ws.{StandaloneWSClient, StandaloneWSResponse}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

case class FileUrl(value: String) {

  require(value.trim.nonEmpty, "Empty file url not allowed")

  override val toString: String = value
}

case class DestinationFileName(value: String) {

  require(value.trim.nonEmpty, "Empty destination file not allowed")

  override val toString: String = value
}

case class DownloadError(message: String)

class FileDownloader(wsClient: StandaloneWSClient)(implicit materializer: Materializer) {

  private val requestTimeout = 5 minutes

  def download(fileUrl: FileUrl, destinationFileName: DestinationFileName): Either[DownloadError, Unit] =
    Await.result(
      wsClient
        .url(fileUrl.toString)
        .withRequestTimeout(requestTimeout)
        .get()
        .flatMap { response =>
          response.status match {
            case 200    => response.toFile(destinationFileName.toString)
            case 404    => Future.successful(Left(DownloadError("A file does not exist")))
            case status => Future.successful(Left(DownloadError(s"Returned status $status")))
          }
        }
        .recover {
          case NonFatal(exception) => Left(DownloadError(exception.getMessage))
        },
      requestTimeout
    )

  private implicit class ResponseOps(response: StandaloneWSResponse)(implicit materializer: Materializer) {
    def toFile(fileName: String): Future[Either[DownloadError, Unit]] =
      response.bodyAsSource
        .runWith(FileIO.toPath(Paths.get(fileName)))
        .map { ioResult =>
          if (ioResult.wasSuccessful) Right(Unit)
          else Left(DownloadError(ioResult.getError.getMessage))
        }
  }
}
