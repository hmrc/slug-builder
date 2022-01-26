/*
 * Copyright 2022 HM Revenue & Customs
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

import cats.implicits._
import play.api.libs.json.Json
import play.api.libs.ws.DefaultBodyWritables._
import play.api.libs.ws._
import uk.gov.hmrc.slugbuilder._

import java.nio.file.{Files, Paths}
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.control.NonFatal

class ArtifactoryConnector(
  wsClient           : StandaloneWSClient,
  fileDownloader     : FileDownloader,
  slugRunnerVersion  : String,
  artifactoryUri     : String,
  artifactoryUsername: String,
  artifactoryPassword: String,
  jdkFileName        : String,
  progressReporter   : ProgressReporter
) {
  private val requestTimeout      = 5.minutes
  private val webstoreVirtualRepo = "webstore"
  private val webstoreLocalRepo   = "webstore-local"

  def downloadJdk(targetFile: String): Either[String, String] = {
    val javaDownloadUri = FileUrl(s"$artifactoryUri/$webstoreVirtualRepo/java/$jdkFileName")
    fileDownloader
      .download(javaDownloadUri, DestinationFileName(targetFile))
      .bimap(
        downloadError => s"JDK couldn't be downloaded from $javaDownloadUri. Cause: $downloadError",
        _             => s"Successfully downloaded JDK from $javaDownloadUri"
      )
  }

  def downloadArtifact(
    repositoryName: RepositoryName,
    releaseVersion: ReleaseVersion,
    targetFile    : ArtifactFileName
  ): Either[String, String] = {

    case class DownloadResult(
      scalaVersion : ScalaVersion,
      fileLocation : DestinationFileName,
      downloadUrl  : FileUrl,
      outcome      : Either[DownloadError, Unit]
    )

    val downloadOutcomes =
      for (scalaVersion <- ScalaVersions.all) yield {
        val downloadUrl =
          FileUrl(s"$artifactoryUri/hmrc-releases/uk/gov/hmrc/${repositoryName}_${scalaVersion}/$releaseVersion/${repositoryName}_${scalaVersion}-$releaseVersion.tgz")
        val fileLocation = DestinationFileName(s"${repositoryName}_${scalaVersion}")
        DownloadResult(scalaVersion, fileLocation, downloadUrl, fileDownloader.download(downloadUrl, fileLocation))
      }

    val (successfulDownloads, failedDownloads) = downloadOutcomes.partition(_.outcome.isRight)

    successfulDownloads match {
      case DownloadResult(_, fileLocation, url, _) :: Nil =>
        Files.move(Paths.get(fileLocation.toString), Paths.get(targetFile.toString))
        Right(s"Successfully downloaded artifact from $url")
      case Nil =>
        Left(s"Could not find artifact. Errors:\n${failedDownloads.map(result => result.downloadUrl.toString + ": " + result.outcome.swap.map(_.message).getOrElse("")).mkString("\n")}")
      case items =>
        Left(s"Multiple artifact versions found for scala versions: ${items.map(_.scalaVersion).mkString(", ")}")
    }
  }

  private def slugUrl(webstoreRepoName: String, repositoryName: RepositoryName, releaseVersion: ReleaseVersion) =
    s"$artifactoryUri/$webstoreRepoName/slugs/$repositoryName/${slugArtifactFileName(repositoryName, releaseVersion)}"

  def slugArtifactFileName(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): String =
    s"${repositoryName}_${releaseVersion}_$slugRunnerVersion.tgz"

  def verifySlugNotCreatedYet(
    repositoryName: RepositoryName,
    releaseVersion: ReleaseVersion
  ): Either[String, String] = {
    val publishUrl = slugUrl(webstoreVirtualRepo, repositoryName, releaseVersion)
    Await.result(
      wsClient
        .url(publishUrl)
        .head()
        .map(_.status)
        .map {
          case 200    => Left(s"Slug already exists at: $publishUrl")
          case 404    => Right(s"Confirmed no slug created yet at $publishUrl")
          case status => Left(s"Cannot check if slug exists at $publishUrl. Returned status $status")
        }
        .recover {
          case NonFatal(exception) =>
            Left(s"Cannot check if slug exists at $publishUrl. Got exception: ${exception.getMessage}")
        },
      requestTimeout
    )
  }

  def publish(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): Either[String, String] = {
    val publishUrl = slugUrl(webstoreLocalRepo, repositoryName, releaseVersion)

    Await.result(
      wsClient
        .url(publishUrl)
        .withRequestTimeout(requestTimeout)
        .withAuth(artifactoryUsername, artifactoryPassword, WSAuthScheme.BASIC)
        .put(Paths.get(slugArtifactFileName(repositoryName, releaseVersion)).toFile)
        .map { response =>
          response.status match {
            case 200 | 201 | 202 | 203 | 204 => Right(s"Successfully published slug to $publishUrl")
            case status =>
              progressReporter.printError(
                s"PUT to $publishUrl returned with errors: ${Json.stringify(Json.parse(response.body))}"
              )
              Left(s"Could not publish slug to $publishUrl. Returned status $status")
          }
        },
      requestTimeout
    )
  }
}
