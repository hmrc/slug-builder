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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

object Main {

  private implicit val system: ActorSystem    = ActorSystem()
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private val progressReporter     = new ProgressReporter()
  private val environmentVariables = new EnvironmentVariables(progressReporter)

  private val httpClient = StandaloneAhcWSClient()

  private val slugBuilder = new SlugBuilder(
    new SlugChecker(
      httpClient,
      environmentVariables.webstoreUri.getOrExit,
      environmentVariables.slugBuilderVersion.getOrExit),
    new ArtifactFetcher(
      httpClient,
      environmentVariables.artifactoryUri.getOrExit
    ),
    progressReporter
  )

  def main(args: Array[String]): Unit = {

    val repositoryName = args.getOrExit(0, "Repository name")
    val releaseVersion = args.getOrExit(1, "Release version")

    Await
      .result(slugBuilder.create(repositoryName, releaseVersion).value, atMost = 2 minutes)
      .fold(
        _ => sys.exit(1),
        _ => sys.exit(0)
      )
  }

  private implicit class ArgsOps(args: Array[String]) {

    def getOrExit(index: Int, argName: String): String =
      if (index >= args.length) {
        progressReporter.printError(s"'$argName' required as argument ${index + 1}.")
        sys.exit(1)
      } else
        args(index)
  }

  private implicit class EitherOps(either: Either[String, String]) {

    lazy val getOrExit: String =
      either.fold(
        error => {
          progressReporter.printError(error)
          sys.exit(1)
        },
        identity
      )
  }
}
