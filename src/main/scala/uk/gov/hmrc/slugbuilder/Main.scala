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
import cats.implicits._
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import uk.gov.hmrc.slugbuilder.connectors.{ArtifactoryConnector, FileDownloader}
import uk.gov.hmrc.slugbuilder.tools.{CliTools, FileUtils, TarArchiver}
import scala.language.postfixOps

object Main {

  private lazy val slugRunnerVersion   = EnvironmentVariables.slugRunnerVersion.getOrExit
  private lazy val artifactoryUri      = EnvironmentVariables.artifactoryUri.getOrExit
  private lazy val artifactoryUsername = EnvironmentVariables.artifactoryUsername.getOrExit
  private lazy val artifactoryPassword = EnvironmentVariables.artifactoryPassword.getOrExit
  private lazy val jdkFileName         = EnvironmentVariables.jdkFileName.getOrExit

  private lazy implicit val system: ActorSystem    = ActorSystem()
  private lazy implicit val mat: ActorMaterializer = ActorMaterializer()

  private lazy val progressReporter = new ProgressReporter()
  private lazy val httpClient       = StandaloneAhcWSClient()
  private lazy val fileDownloader   = new FileDownloader(httpClient)

  private lazy val slugBuilder = new SlugBuilder(
    progressReporter,
    new ArtifactoryConnector(
      httpClient,
      fileDownloader,
      slugRunnerVersion,
      artifactoryUri,
      artifactoryUsername,
      artifactoryPassword,
      jdkFileName,
      progressReporter),
    new TarArchiver(new CliTools(progressReporter)),
    new StartDockerScriptCreator(),
    new FileUtils()
  )

  def main(args: Array[String]): Unit = {

    val repositoryName = args.get("Repository name", atIdx = 0).flatMap(RepositoryName.create).getOrExit
    val releaseVersion = args.get("Release version", atIdx = 1).flatMap(ReleaseVersion.create).getOrExit

    slugBuilder
      .create(repositoryName, releaseVersion)
      .fold(
        _ => sys.exit(1),
        _ => sys.exit(0)
      )
  }

  private implicit class ArgsOps(args: Array[String]) {

    def get(argName: String, atIdx: Int): Either[String, String] =
      if (atIdx >= args.length) {
        Left(s"'$argName' required as argument ${atIdx + 1}.")
      } else
        Right(args(atIdx))
  }

  private implicit class EitherOps[T](either: Either[String, T]) {

    lazy val getOrExit: T =
      either.fold(
        error => {
          progressReporter.printError(error)
          sys.exit(1)
        },
        identity
      )
  }
}
