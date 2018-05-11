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

import java.nio.file.Paths

import akka.stream.scaladsl.FileIO
import akka.stream.{IOResult, Materializer}
import play.api.libs.ws.StandaloneWSResponse

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

private[slugbuilder] object HttpClientTools {

  implicit class ResponseOps(response: StandaloneWSResponse)(implicit materializer: Materializer) {
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
