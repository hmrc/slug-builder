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

import java.io.File
import java.nio.file.{Files, Paths}
import org.scalamock.scalatest.MockFactory
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.ws.DefaultBodyWritables._
import play.api.libs.ws.{BodyWritable, StandaloneWSClient, WSAuthScheme}
import uk.gov.hmrc.slugbuilder.generators.Generators.Implicits._
import uk.gov.hmrc.slugbuilder.generators.Generators._
import uk.gov.hmrc.slugbuilder.tools.ProgressReporterStub
import scala.concurrent.Future

class SlugUtilSpec extends WordSpec with MockFactory with ScalaFutures {
  "verifySlugNotCreatedYet" should {
    "return Left if slug already exists" in new Setup {
      (wsRequest.head _)
        .expects()
        .returning(Future.successful(wsResponse))

      (wsResponse.status _)
        .expects()
        .returning(200)

      slugUtil.verifySlugNotCreatedYet(repositoryName, releaseVersion) shouldBe
        Left(s"Slug already exists at: $url")
    }

    "return Right if slug does not exist" in new Setup {
      (wsRequest.head _)
        .expects()
        .returning(Future.successful(wsResponse))

      (wsResponse.status _)
        .expects()
        .returning(404)

      slugUtil.verifySlugNotCreatedYet(repositoryName, releaseVersion) shouldBe
        Right(s"No slug created yet at $url")
    }

    "return Left when got unexpected status from checking if slug exists" in {
      allHttpStatusCodes filterNot Seq(200, 404).contains foreach { status =>
        new Setup {
          (wsRequest.head _)
            .expects()
            .returning(Future.successful(wsResponse))

          (wsResponse.status _)
            .expects()
            .returning(status)

          slugUtil.verifySlugNotCreatedYet(repositoryName, releaseVersion) shouldBe
            Left(s"Cannot check if slug exists at $url. Returned status $status")
        }
      }
    }

    "return Left if calling webstore results in an exception" in new Setup {
      val exception = new Exception("some error")
      (wsRequest.head _)
        .expects()
        .returning(Future.failed(exception))

      slugUtil.verifySlugNotCreatedYet(repositoryName, releaseVersion) shouldBe
        Left(s"Cannot check if slug exists at $url. Got exception: ${exception.getMessage}")
    }
  }

  "publish" should {
    "do a PUT with proper authentication" in new Setup {

      val publishUrl =
        s"$artifactoryUri/webstore/slugs/$repositoryName/${repositoryName}_${releaseVersion}_$slugBuilderVersion.tgz"

      val fileToUpload = Paths.get(s"${repositoryName}_${releaseVersion}_$slugBuilderVersion.tgz")
      Files.write(fileToUpload, "some content".getBytes())

      (wsRequest
        .withAuth(_: String, _: String, _: WSAuthScheme))
        .expects(artifactoryUsername, artifactoryPassword, WSAuthScheme.BASIC)
        .returning(wsRequest)

      (wsRequest
        .put(_: File)(_: BodyWritable[File]))
        .expects(fileToUpload.toFile, implicitly[BodyWritable[File]])
        .returning(Future.successful(wsResponse))

      (wsResponse.status _)
        .expects()
        .returning(201)

      slugUtil.publish(repositoryName, releaseVersion) shouldBe Right(s"Slug published successfully to $publishUrl")

      fileToUpload.toFile.delete()
    }

    "handle errors" in new Setup {

      val publishUrl =
        s"$artifactoryUri/webstore/slugs/$repositoryName/${repositoryName}_${releaseVersion}_$slugBuilderVersion.tgz"

      val fileToUpload = Paths.get(s"${repositoryName}_${releaseVersion}_$slugBuilderVersion.tgz")
      Files.write(fileToUpload, "some content".getBytes())

      (wsRequest
        .withAuth(_: String, _: String, _: WSAuthScheme))
        .expects(artifactoryUsername, artifactoryPassword, WSAuthScheme.BASIC)
        .returning(wsRequest)

      (wsRequest
        .put(_: File)(_: BodyWritable[File]))
        .expects(fileToUpload.toFile, implicitly[BodyWritable[File]])
        .returning(Future.successful(wsResponse))

      (wsResponse.status _)
        .expects()
        .returning(403)
      val responseBody = """|{
                           |  "errors" : [ {
                           |    "status" : 403,
                           |    "message" : "Some ERROR"
                           |  } ]
                           |}""".stripMargin
      (wsResponse.body _)
        .expects()
        .returning(responseBody)

      slugUtil.publish(repositoryName, releaseVersion) shouldBe Left(
        s"Could not publish slug to $publishUrl. Returned status 403")

      progressReporter.logs should contain(
        s"""PUT to $publishUrl returned with errors: {"errors":[{"status":403,"message":"Some ERROR"}]}""")

      fileToUpload.toFile.delete()
    }
  }

  private trait Setup {
    val artifactoryUri       = "https://artifactory"
    val artifactoryUsername  = "username"
    val artifactoryPassword  = "password"
    val slugBuilderVersion   = nonEmptyStrings.generateOne
    val artifactName         = nonEmptyStrings.generateOne
    val wsClient             = mock[StandaloneWSClient]
    val repositoryName       = repositoryNameGen.generateOne
    val releaseVersion       = releaseVersionGen.generateOne
    val slugArtifactFilename = s"${repositoryName}_${releaseVersion}_$slugBuilderVersion.tgz"
    val progressReporter     = new ProgressReporterStub

    val slugUtil = new SlugUtil(
      wsClient,
      slugBuilderVersion,
      artifactoryUri,
      artifactoryUsername,
      artifactoryPassword,
      progressReporter)

    val url        = s"$artifactoryUri/webstore/slugs/$repositoryName/$slugArtifactFilename"
    val wsRequest  = mock[TestWSRequest]
    val wsResponse = mock[wsRequest.Response]

    (wsClient
      .url(_: String))
      .expects(url)
      .returning(wsRequest)

  }
}
