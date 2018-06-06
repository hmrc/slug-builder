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
import play.api.libs.json.Json
import play.api.libs.ws._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.control.NonFatal
import play.api.libs.ws.DefaultBodyWritables._

class SlugUtil(
  wsClient: StandaloneWSClient,
  slugBuilderVersion: String,
  artifactoryUri: String,
  artifactoryUsername: String,
  artifactoryPassword: String,
  progressReporter: ProgressReporter) {

  def slugUrl(repositoryName: RepositoryName, releaseVersion: ReleaseVersion) =
    s"$artifactoryUri/webstore/slugs/$repositoryName/${slugArtifactFileName(repositoryName, releaseVersion)}"

  def slugArtifactFileName(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): String =
    s"${repositoryName}_${releaseVersion}_$slugBuilderVersion.tgz"

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
