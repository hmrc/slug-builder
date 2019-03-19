/*
 * Copyright 2019 HM Revenue & Customs
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
import java.nio.file.{Files, Paths}

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

  private val requestTimeout      = 5 minutes
  private val webstoreVirtualRepo = "webstore"
  private val webstoreLocalRepo   = "webstore-local"

  def downloadAppConfigBase(repositoryName: RepositoryName): Either[String, String] = {

    val fileUrl = FileUrl(s"$artifactoryUri/$webstoreVirtualRepo/app-config-base/$repositoryName.conf")

    fileDownloader
      .download(fileUrl, DestinationFileName(AppConfigBaseFileName(repositoryName).toString))
      .bimap(
        downloadError => s"app-config-base couldn't be downloaded from $fileUrl. Cause: $downloadError",
        _ => s"Successfully downloaded app-config-base from $fileUrl"
      )
  }

  def downloadJdk(targetFile: String): Either[String, String] = {

    val javaDownloadUri = FileUrl(s"$artifactoryUri/$webstoreVirtualRepo/java/$jdkFileName")
    fileDownloader
      .download(javaDownloadUri, DestinationFileName(targetFile))
      .bimap(
        downloadError => s"JDK couldn't be downloaded from $javaDownloadUri. Cause: $downloadError",
        _ => s"Successfully downloaded JDK from $javaDownloadUri"
      )
  }

  def downloadArtifact(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): Either[String, String] = {
    val artifactFileName = ArtifactFileName(repositoryName, releaseVersion)

    def fileUrl(scalaVersion: ScalaVersion) =
      FileUrl(s"$artifactoryUri/hmrc-releases/uk/gov/hmrc/${repositoryName}_${scalaVersion}/$releaseVersion/${repositoryName}_${scalaVersion}-$releaseVersion.tgz")

    def targetFileName(scalaVersion: ScalaVersion) = DestinationFileName(s"${artifactFileName}_${scalaVersion}")

    def loop(outcome: Either[Seq[String], String], scalaVersions: List[ScalaVersion]): Either[String, String] = {
      (outcome, scalaVersions) match {
        case (prev@Right(msg), scalaVersion :: tail) =>
          val url = fileUrl(scalaVersion)
          val targetFile = targetFileName(scalaVersion)
          fileDownloader.download(url, targetFile) match {
            case Left(_) =>
              loop(prev, tail)
            case Right(_) =>
              Left(s"Multiple version of scala artifact match - also in $scalaVersion. Caused by $prev")
          }
        case (Left(errors), scalaVersion :: tail) =>
          val target = targetFileName(scalaVersion)
          val url = fileUrl(scalaVersion)
          fileDownloader.download(url, target) match {
            case Left(error) =>
              loop(Left(errors :+ s"Artifact couldn't be downloaded from $url. Cause: $error"), tail)
            case Right(_) =>
              Files.move(Paths.get(target.toString), Paths.get(artifactFileName.toString))
              loop(Right(s"Successfully downloaded artifact from $url"), tail)
          }

        case (Left(errors), Nil) => Left("Could not find artifact: Caused by" + errors.mkString("\n\t", "\n\t", "\n"))

        case (Right(message), Nil) => Right(message)
      }
    }
    loop(Left(Seq.empty), ScalaVersions.all)
  }

  private def slugUrl(webstoreRepoName: String, repositoryName: RepositoryName, releaseVersion: ReleaseVersion) =
    s"$artifactoryUri/$webstoreRepoName/slugs/$repositoryName/${slugArtifactFileName(repositoryName, releaseVersion)}"

  def slugArtifactFileName(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): String =
    s"${repositoryName}_${releaseVersion}_$slugRunnerVersion.tgz"

  def verifySlugNotCreatedYet(
    repositoryName: RepositoryName,
    releaseVersion: ReleaseVersion): Either[String, String] = {
    val publishUrl = slugUrl(webstoreVirtualRepo, repositoryName, releaseVersion)
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

  def unpublish(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): Either[String, String] = {
    val unpublishUrl = slugUrl(webstoreLocalRepo, repositoryName, releaseVersion)

    Await.result(
      wsClient
        .url(unpublishUrl)
        .withAuth(artifactoryUsername, artifactoryPassword, WSAuthScheme.BASIC)
        .delete()
        .map { response =>
          response.status match {
            case 204 => Right(s"Slug unpublished successfully from $unpublishUrl")
            case 404 => Right(s"Nothing to do: slug does not exist in $unpublishUrl")
            case status =>
              progressReporter.printError(
                s"DELETE from $unpublishUrl returned with errors: ${Json.stringify(Json.parse(response.body))}"
              )
              Left(s"Could not unpublish slug from $unpublishUrl. Returned status $status")
          }
        },
      requestTimeout
    )
  }
}
