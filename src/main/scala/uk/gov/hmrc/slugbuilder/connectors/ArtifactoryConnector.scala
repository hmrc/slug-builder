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

package uk.gov.hmrc.slugbuilder.connectors
import java.nio.file.Paths
import cats.implicits._
import play.api.libs.json.Json
import play.api.libs.ws.DefaultBodyWritables._
import play.api.libs.ws._
import uk.gov.hmrc.slugbuilder._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.control.NonFatal

class ArtifactoryConnector(
  wsClient: StandaloneWSClient,
  fileDownloader: FileDownloader,
  slugRunnerVersion: String,
  artifactoryUri: String,
  artifactoryUsername: String,
  artifactoryPassword: String,
  jdkFileName: String,
  progressReporter: ProgressReporter) {

  def downloadAppConfigBase(repositoryName: RepositoryName): Either[String, String] = {

    val fileUrl = FileUrl(s"$artifactoryUri/webstore/app-config-base/$repositoryName.conf")

    fileDownloader
      .download(fileUrl, DestinationFileName(AppConfigBaseFileName(repositoryName).toString))
      .bimap(
        downloadError => s"app-config-base couldn't be downloaded from $fileUrl. Cause: $downloadError",
        _ => s"app-config-base successfully downloaded from $fileUrl"
      )
  }

  def downloadJdk(targetFile: String): Either[String, String] = {

    val javaDownloadUri = FileUrl(s"$artifactoryUri/webstore/java/$jdkFileName")
    fileDownloader
      .download(javaDownloadUri, DestinationFileName(targetFile))
      .bimap(
        downloadError => s"JDK couldn't be downloaded from $javaDownloadUri. Cause: $downloadError",
        _ => s"Successfully downloaded JDK from $javaDownloadUri"
      )
  }

  def downloadArtifact(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): Either[String, String] = {

    val fileUrl = FileUrl(
      s"$artifactoryUri/hmrc-releases/uk/gov/hmrc/${repositoryName}_2.11/$releaseVersion/${repositoryName}_2.11-$releaseVersion.tgz"
    )

    fileDownloader
      .download(fileUrl, DestinationFileName(ArtifactFileName(repositoryName, releaseVersion).toString))
      .bimap(
        downloadError => s"Artifact couldn't be downloaded from $fileUrl. Cause: $downloadError",
        _ => s"Artifact successfully downloaded from $fileUrl"
      )
  }

  def slugUrl(repositoryName: RepositoryName, releaseVersion: ReleaseVersion) =
    s"$artifactoryUri/webstore/slugs/$repositoryName/${slugArtifactFileName(repositoryName, releaseVersion)}"

  def slugArtifactFileName(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): String =
    s"${repositoryName}_${releaseVersion}_$slugRunnerVersion.tgz"

  def verifySlugNotCreatedYet(
    repositoryName: RepositoryName,
    releaseVersion: ReleaseVersion): Either[String, String] = {
    val publishUrl = slugUrl(repositoryName, releaseVersion)
    Await.result(
      wsClient
        .url(publishUrl)
        .head()
        .map(_.status)
        .map {
          case 200    => Left(s"Slug already exists at: $publishUrl")
          case 404    => Right(s"No slug created yet at $publishUrl")
          case status => Left(s"Cannot check if slug exists at $publishUrl. Returned status $status")
        }
        .recover {
          case NonFatal(exception) =>
            Left(s"Cannot check if slug exists at $publishUrl. Got exception: ${exception.getMessage}")
        },
      2 minutes
    )
  }

  def publish(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): Either[String, String] = {
    val publishUrl = slugUrl(repositoryName, releaseVersion)

    Await.result(
      wsClient
        .url(publishUrl)
        .withAuth(artifactoryUsername, artifactoryPassword, WSAuthScheme.BASIC)
        .put(Paths.get(slugArtifactFileName(repositoryName, releaseVersion)).toFile)
        .map { response =>
          response.status match {
            case 200 | 201 | 202 | 203 | 204 => Right(s"Slug published successfully to $publishUrl")
            case status =>
              progressReporter.printError(
                s"PUT to $publishUrl returned with errors: ${Json.stringify(Json.parse(response.body))}")
              Left(s"Could not publish slug to $publishUrl. Returned status $status")
          }
        },
      2 minutes
    )
  }
}
