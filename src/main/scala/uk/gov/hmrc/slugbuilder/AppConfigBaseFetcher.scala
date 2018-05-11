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

import akka.stream.{IOResult, Materializer}
import cats.data.EitherT
import play.api.libs.ws.StandaloneWSClient
import uk.gov.hmrc.slugbuilder.tools.HttpClientTools._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

class AppConfigBaseFetcher(wSClient: StandaloneWSClient, webstoreUri: String)(implicit materializer: Materializer) {

  def download(repositoryName: RepositoryName): EitherT[Future, String, String] = EitherT[Future, String, String] {

    val url = s"$webstoreUri/app-config-base/$repositoryName.conf"

    wSClient
      .url(url)
      .get()
      .flatMap { response =>
        response.status match {
          case 200 =>
            response.toFile(s"$repositoryName.conf")(
              onSuccess = Right(s"app-config-base successfully downloaded from $url"),
              onFailure = (ioResult: IOResult) =>
                Left(s"app-config-base couldn't be downloaded from $url. Cause: ${ioResult.getError.getMessage}")
            )
          case 404    => Future.successful(Left(s"app-config-base does not exist at: $url"))
          case status => Future.successful(Left(s"Cannot fetch app-config-base from $url. Returned status $status"))
        }
      }
      .recover {
        case NonFatal(exception) =>
          Left(s"Cannot fetch app-config-base from $url. Got exception: ${exception.getMessage}")
      }
  }
}
