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

import java.io.File
import java.nio.file.{Files, Paths}

import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.ws.DefaultBodyWritables._
import play.api.libs.ws.{BodyWritable, StandaloneWSClient, WSAuthScheme}
import uk.gov.hmrc.slugbuilder.generators.Generators.Implicits._
import uk.gov.hmrc.slugbuilder.generators.Generators.{allHttpStatusCodes, nonEmptyStrings, releaseVersionGen, repositoryNameGen}
import uk.gov.hmrc.slugbuilder.tools._
import uk.gov.hmrc.slugbuilder.{AdditionalBinary, AppConfigBaseFileName, ArtifactFileName, TestWSRequest}

import scala.concurrent.Future
import scala.concurrent.duration._

class ArtifactoryConnectorSpec extends WordSpec with MockFactory with ScalaFutures with Matchers {

  "verifySlugNotCreatedYet" should {

    "return Left if slug already exists" in new Setup {
      val url = s"$artifactoryUri/webstore/slugs/$repositoryName/$slugArtifactFilename"
      (wsClient
        .url(_: String))
        .expects(url)
        .returning(wsRequest)

      (wsRequest.head _)
        .expects()
        .returning(Future.successful(wsResponse))

      (wsResponse.status _)
        .expects()
        .returning(200)

      connector.verifySlugNotCreatedYet(repositoryName, releaseVersion) shouldBe
        Left(s"Slug already exists at: $url")
    }

    "return Right if slug does not exist" in new Setup {
      val url = s"$artifactoryUri/webstore/slugs/$repositoryName/$slugArtifactFilename"
      (wsClient
        .url(_: String))
        .expects(url)
        .returning(wsRequest)

      (wsRequest.head _)
        .expects()
        .returning(Future.successful(wsResponse))

      (wsResponse.status _)
        .expects()
        .returning(404)

      connector.verifySlugNotCreatedYet(repositoryName, releaseVersion) shouldBe
        Right(s"No slug created yet at $url")
    }

    "return Left when got unexpected status from checking if slug exists" in {
      allHttpStatusCodes filterNot Seq(200, 404).contains foreach { status =>
        new Setup {
          val url = s"$artifactoryUri/webstore/slugs/$repositoryName/$slugArtifactFilename"
          (wsClient
            .url(_: String))
            .expects(url)
            .returning(wsRequest)

          (wsRequest.head _)
            .expects()
            .returning(Future.successful(wsResponse))

          (wsResponse.status _)
            .expects()
            .returning(status)

          connector.verifySlugNotCreatedYet(repositoryName, releaseVersion) shouldBe
            Left(s"Cannot check if slug exists at $url. Returned status $status")
        }
      }
    }

    "return Left if calling webstore results in an exception" in new Setup {
      val url = s"$artifactoryUri/webstore/slugs/$repositoryName/$slugArtifactFilename"
      (wsClient
        .url(_: String))
        .expects(url)
        .returning(wsRequest)

      val exception = new Exception("some error")
      (wsRequest.head _)
        .expects()
        .returning(Future.failed(exception))

      connector.verifySlugNotCreatedYet(repositoryName, releaseVersion) shouldBe
        Left(s"Cannot check if slug exists at $url. Got exception: ${exception.getMessage}")
    }
  }

  "download From Webstore" should {

    "return Right if service's app-config-base can be downloaded from Webstore successfully" in new Setup {

      val fileUrl             = FileUrl(s"$artifactoryUri/webstore/app-config-base/$repositoryName.conf")
      val destinationFileName = DestinationFileName(AppConfigBaseFileName(repositoryName).toString)

      (fileDownloader
        .download(_: FileUrl, _: DestinationFileName))
        .expects(fileUrl, destinationFileName)
        .returning(Right())

      connector
        .downloadAppConfigBase(repositoryName) shouldBe Right(s"Successfully downloaded app-config-base from $fileUrl")
    }

    "return Left if there was an error when downloading app-config-base from Webstore" in new Setup {

      val fileUrl             = FileUrl(s"$artifactoryUri/webstore/app-config-base/$repositoryName.conf")
      val destinationFileName = DestinationFileName(AppConfigBaseFileName(repositoryName).toString)

      val downloadingProblem = DownloadError("downloading problem")
      (fileDownloader
        .download(_: FileUrl, _: DestinationFileName))
        .expects(fileUrl, destinationFileName)
        .returning(Left(downloadingProblem))

      connector.downloadAppConfigBase(repositoryName) shouldBe
        Left(s"app-config-base couldn't be downloaded from $fileUrl. Cause: $downloadingProblem")
    }
  }

  "download additional binaries" should {
    "preserve left error messages" in new Setup {

      val fileUrl1 = artifactoryUri+"/newFile"
      val fileName1 = "newFile"
      val fileUrl2 = artifactoryUri+"/newFile2"
      val fileName2 = "newFile2"

      val files = Seq(AdditionalBinary(fileUrl1, Paths.get(fileName1)),
                      AdditionalBinary(fileUrl2, Paths.get(fileName2)))

      (fileDownloader
        .download(_:FileUrl, _:DestinationFileName))
        .expects(FileUrl(fileUrl1), DestinationFileName(fileName1))
        .returning(Right())

      (fileDownloader
        .download(_:FileUrl, _:DestinationFileName))
        .expects(FileUrl(fileUrl2), DestinationFileName(fileName2))
        .returning(Left(DownloadError("A file does not exist")))

      connector.downloadAdditionalBinaries(files) shouldBe Left(
        s"Couldn't download artifact from https://artifactory/newFile2. Cause: DownloadError(A file does not exist)\n"
      )
    }

    "keep two success messages" in new Setup {

      val fileUrl1 = artifactoryUri+"/newFile"
      val fileName1 = "newFile"
      val fileUrl2 = artifactoryUri+"/newFile2"
      val fileName2 = "newFile2"

      val files = Seq(AdditionalBinary(fileUrl1, Paths.get(fileName1)),
                      AdditionalBinary(fileUrl2, Paths.get(fileName2)))

      (fileDownloader
        .download(_:FileUrl, _:DestinationFileName))
        .expects(FileUrl(fileUrl1), DestinationFileName(fileName1))
        .returning(Right())

      (fileDownloader
        .download(_:FileUrl, _:DestinationFileName))
        .expects(FileUrl(fileUrl2), DestinationFileName(fileName2))
        .returning(Right())

      connector.downloadAdditionalBinaries(files) shouldBe Right(
        s"Successfully downloaded artifact from https://artifactory/newFile\nSuccessfully downloaded artifact from https://artifactory/newFile2\n"
      )

    }
  }

  "downloadArtifact" should {

    "return Right if artifact can be downloaded from Artifactory successfully" in new Setup {
      val fileUrl = FileUrl(
        s"$artifactoryUri/hmrc-releases/uk/gov/hmrc/${repositoryName}_2.11/$releaseVersion/${repositoryName}_2.11-$releaseVersion.tgz"
      )
      val destinationFileName = DestinationFileName(ArtifactFileName(repositoryName, releaseVersion).toString)
      (fileDownloader
        .download(_: FileUrl, _: DestinationFileName))
        .expects(fileUrl, destinationFileName)
        .returning(Right())

      connector.downloadArtifact(repositoryName, releaseVersion) shouldBe
        Right(s"Successfully downloaded artifact from $fileUrl")
    }

    "return Left if there was an error when downloading the artifact from Artifactory" in new Setup {
      val downloadingProblem = DownloadError("downloading problem")
      val fileUrl = FileUrl(
        s"$artifactoryUri/hmrc-releases/uk/gov/hmrc/${repositoryName}_2.11/$releaseVersion/${repositoryName}_2.11-$releaseVersion.tgz"
      )
      val destinationFileName = DestinationFileName(ArtifactFileName(repositoryName, releaseVersion).toString)

      (fileDownloader
        .download(_: FileUrl, _: DestinationFileName))
        .expects(fileUrl, destinationFileName)
        .returning(Left(downloadingProblem))

      connector.downloadArtifact(repositoryName, releaseVersion) shouldBe
        Left(s"Artifact couldn't be downloaded from $fileUrl. Cause: $downloadingProblem")
    }
  }

  "publish" should {
    "do a PUT with proper authentication" in new Setup {
      val slugUrl = s"$artifactoryUri/webstore-local/slugs/$repositoryName/$slugArtifactFilename"
      (wsClient
        .url(_: String))
        .expects(slugUrl)
        .returning(wsRequest)

      val fileToUpload = Paths.get(s"${repositoryName}_${releaseVersion}_$slugRunnerVersion.tgz")
      Files.write(fileToUpload, "some content".getBytes())

      (wsRequest
        .withRequestTimeout(_: Duration))
        .expects(5 minutes)
        .returning(wsRequest)

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

      connector.publish(repositoryName, releaseVersion) shouldBe Right(s"Successfully published slug to $slugUrl")

      fileToUpload.toFile.delete()
    }

    "handle errors" in new Setup {
      val slugUrl = s"$artifactoryUri/webstore-local/slugs/$repositoryName/$slugArtifactFilename"
      (wsClient
        .url(_: String))
        .expects(slugUrl)
        .returning(wsRequest)

      val fileToUpload = Paths.get(s"${repositoryName}_${releaseVersion}_$slugRunnerVersion.tgz")
      Files.write(fileToUpload, "some content".getBytes())

      (wsRequest
        .withRequestTimeout(_: Duration))
        .expects(5 minutes)
        .returning(wsRequest)

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

      connector.publish(repositoryName, releaseVersion) shouldBe Left(
        s"Could not publish slug to $slugUrl. Returned status 403")

      progressReporter.logs should contain(
        s"""PUT to $slugUrl returned with errors: {"errors":[{"status":403,"message":"Some ERROR"}]}""")

      fileToUpload.toFile.delete()
    }
  }

  "unpublish" should {
    "do a DELETE with proper authentication" in new Setup {
      val slugUrl = s"$artifactoryUri/webstore-local/slugs/$repositoryName/$slugArtifactFilename"

      (wsClient
        .url(_: String))
        .expects(slugUrl)
        .returning(wsRequest)

      (wsRequest
        .withAuth(_: String, _: String, _: WSAuthScheme))
        .expects(artifactoryUsername, artifactoryPassword, WSAuthScheme.BASIC)
        .returning(wsRequest)

      (wsRequest.delete _)
        .expects()
        .returning(Future.successful(wsResponse))

      (wsResponse.status _)
        .expects()
        .returning(204)

      connector.unpublish(repositoryName, releaseVersion) shouldBe Right(s"Slug unpublished successfully from $slugUrl")
    }

    "not do anything is the slug does not exist" in new Setup {
      val slugUrl = s"$artifactoryUri/webstore-local/slugs/$repositoryName/$slugArtifactFilename"

      (wsClient
        .url(_: String))
        .expects(slugUrl)
        .returning(wsRequest)

      (wsRequest
        .withAuth(_: String, _: String, _: WSAuthScheme))
        .expects(artifactoryUsername, artifactoryPassword, WSAuthScheme.BASIC)
        .returning(wsRequest)

      (wsRequest.delete _)
        .expects()
        .returning(Future.successful(wsResponse))

      (wsResponse.status _)
        .expects()
        .returning(404)

      connector.unpublish(repositoryName, releaseVersion) shouldBe Right(
        s"Nothing to do: slug does not exist in $slugUrl")
    }

    "handle errors" in new Setup {
      val slugurl = s"$artifactoryUri/webstore-local/slugs/$repositoryName/$slugArtifactFilename"

      (wsClient
        .url(_: String))
        .expects(slugurl)
        .returning(wsRequest)

      (wsRequest
        .withAuth(_: String, _: String, _: WSAuthScheme))
        .expects(artifactoryUsername, artifactoryPassword, WSAuthScheme.BASIC)
        .returning(wsRequest)

      (wsRequest.delete _)
        .expects()
        .returning(Future.successful(wsResponse))

      (wsResponse.status _)
        .expects()
        .returning(401)
      val responseBody = """|{
                            |  "errors" : [ {
                            |    "status" : 401,
                            |    "message" : "Some ERROR"
                            |  } ]
                            |}""".stripMargin
      (wsResponse.body _)
        .expects()
        .returning(responseBody)

      connector.unpublish(repositoryName, releaseVersion) shouldBe Left(
        s"Could not unpublish slug from $slugurl. Returned status 401")
    }

  }

  private trait Setup {
    val artifactoryUri       = "https://artifactory"
    val artifactoryUsername  = "username"
    val artifactoryPassword  = "password"
    val slugRunnerVersion    = nonEmptyStrings.generateOne
    val artifactName         = nonEmptyStrings.generateOne
    val wsClient             = mock[StandaloneWSClient]
    val repositoryName       = repositoryNameGen.generateOne
    val releaseVersion       = releaseVersionGen.generateOne
    val jdkFileName          = "jdk.tgz"
    val slugArtifactFilename = s"${repositoryName}_${releaseVersion}_$slugRunnerVersion.tgz"

    val progressReporter = new ProgressReporterStub

    val fileDownloader = mock[FileDownloader]
    val connector = new ArtifactoryConnector(
      wsClient,
      fileDownloader,
      slugRunnerVersion,
      artifactoryUri,
      artifactoryUsername,
      artifactoryPassword,
      jdkFileName,
      progressReporter)

    val wsRequest  = mock[TestWSRequest]
    val wsResponse = mock[wsRequest.Response]

  }
}
