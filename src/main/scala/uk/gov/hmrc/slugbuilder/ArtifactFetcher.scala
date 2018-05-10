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

import java.nio.file.Paths

import akka.stream.scaladsl.FileIO
import akka.stream.{IOResult, Materializer}
import cats.data.EitherT
import play.api.libs.ws.{StandaloneWSClient, StandaloneWSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

class ArtifactFetcher(wSClient: StandaloneWSClient, artifactoryUri: String)(implicit materializer: Materializer) {

  def download(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): EitherT[Future, String, String] =
    EitherT[Future, String, String] {

      val url =
        s"$artifactoryUri/uk/gov/hmrc/${repositoryName}_2.11/$releaseVersion/${repositoryName}_2.11-$releaseVersion.tgz"

      wSClient
        .url(url)
        .get()
        .flatMap { response =>
          response.status match {
            case 200 =>
              response.toFile(s"$repositoryName-$releaseVersion.tgz")(
                onSuccess = Right(s"Artifact successfully downloaded from $url"),
                onFailure = (ioResult: IOResult) =>
                  Left(s"Artifact couldn't be downloaded from $url. Cause: ${ioResult.getError.getMessage}")
              )
            case 404    => Future.successful(Left(s"Artifact does not exist at: $url"))
            case status => Future.successful(Left(s"Cannot fetch artifact from $url. Returned status $status"))
          }
        }
        .recover {
          case NonFatal(exception) =>
            Left(s"Cannot fetch artifact from $url. Got exception: ${exception.getMessage}")
        }
    }

  private implicit class ResponseOps(response: StandaloneWSResponse) {
    def toFile(fileName: String)(
      onSuccess: => Either[String, String],
      onFailure: IOResult => Either[String, String]): Future[Either[String, String]] =
      response.bodyAsSource
        .runWith(FileIO.toPath(Paths.get(fileName)))
        .map { ioResult =>
          if (ioResult.wasSuccessful) onSuccess
          else onFailure(ioResult)
        }
  }
}
