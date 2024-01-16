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

import org.apache.pekko.stream.{IOOperationIncompleteException, Materializer}
import org.apache.pekko.stream.scaladsl.FileIO
import play.api.libs.ws.{StandaloneWSClient, StandaloneWSResponse}

import java.nio.file.Paths
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
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

  private val requestTimeout = 5.minutes

  def download(
    fileUrl            : FileUrl,
    destinationFileName: DestinationFileName,
    headers            : Map[String, String] = Map.empty
  ): Either[DownloadError, Unit] =
    Await.result(
      wsClient
        .url(fileUrl.toString)
        .addHttpHeaders(headers.toList: _*)
        .withRequestTimeout(requestTimeout)
        .get()
        .flatMap { response =>
          response.status match {
            case 200    => toFile(response, destinationFileName.toString).map(_ => Right(()))
            case 404    => Future.successful(Left(DownloadError("A file does not exist")))
            case status => Future.successful(Left(DownloadError(s"Returned status $status")))
          }
        }
        .recover {
          case e: IOOperationIncompleteException if e.getCause != null => Left(DownloadError(e.getCause.getMessage))
          case NonFatal(exception) => Left(DownloadError(exception.getMessage))
        },
      requestTimeout
    )

  private def toFile(response: StandaloneWSResponse, fileName: String): Future[Unit] =
    response.bodyAsSource
      .runWith(FileIO.toPath(Paths.get(fileName)))
      .map(_ => ())
}
