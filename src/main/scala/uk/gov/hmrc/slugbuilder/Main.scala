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

package uk.gov.hmrc.slugbuilder

import org.apache.pekko.actor.ActorSystem
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import uk.gov.hmrc.slugbuilder.ArgParser.{Build, Publish}
import uk.gov.hmrc.slugbuilder.connectors.{ArtifactoryConnector, FileDownloader}
import uk.gov.hmrc.slugbuilder.tools.{CliTools, FileUtils, TarArchiver}

object Main {

  private val progressReporter = new ProgressReporter()

  private val slugRunnerVersion    = EnvironmentVariables.slugRunnerVersion.getOrExit
  private val artifactoryUri       = EnvironmentVariables.artifactoryUri.getOrExit
  private val artifactoryUsername  = EnvironmentVariables.artifactoryUsername.getOrExit
  private val artifactoryPassword  = EnvironmentVariables.artifactoryPassword.getOrExit
  private val jdkFileName          = EnvironmentVariables.jdkFileName.getOrExit
  private val slugRuntimeJavaOpts  = EnvironmentVariables.slugRuntimeJavaOpts
  private val artefactLocation     = EnvironmentVariables.artefactLocation.toOption
  private val environmentVariables = EnvironmentVariables.all
  private val includeFiles         = EnvironmentVariables.includeFiles

  private implicit val system: ActorSystem = ActorSystem()

  private val httpClient       = StandaloneAhcWSClient()
  private val fileDownloader   = new FileDownloader(httpClient)

  private val artifactoryConnector =
    new ArtifactoryConnector(
      httpClient,
      fileDownloader,
      slugRunnerVersion,
      artifactoryUri,
      artifactoryUsername,
      artifactoryPassword,
      jdkFileName,
      progressReporter
    )

  private lazy val slugBuilder = new SlugBuilder(
    progressReporter,
    artifactoryConnector,
    new TarArchiver(new CliTools(progressReporter)),
    new StartDockerScriptCreator(),
    new FileUtils()
  )

  def main(args: Array[String]): Unit =
    (ArgParser.parse(args).getOrExit match {
      case Build(repositoryName, releaseVersion) =>
        slugBuilder
          .create(
            repositoryName,
            releaseVersion,
            slugRuntimeJavaOpts,
            environmentVariables,
            includeFiles,
            artefactLocation,
            publish = false
          )
      case Publish(repositoryName, releaseVersion) =>
        slugBuilder
          .create(
            repositoryName,
            releaseVersion,
            slugRuntimeJavaOpts,
            environmentVariables,
            includeFiles,
            artefactLocation,
            publish = true
          )
    }).fold(
      _ => sys.exit(1),
      _ => sys.exit(0)
    )

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
