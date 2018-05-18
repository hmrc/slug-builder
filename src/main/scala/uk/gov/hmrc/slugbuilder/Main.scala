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
import org.eclipse.jgit.api.Git
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import uk.gov.hmrc.slugbuilder.functions.SlugArtifactName
import uk.gov.hmrc.slugbuilder.tools.TarArchiver

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

object Main {

  private lazy val webstoreUri        = EnvironmentVariables.webstoreUri.getOrExit
  private lazy val slugBuilderVersion = EnvironmentVariables.slugBuilderVersion.getOrExit
  private lazy val artifactoryUri     = EnvironmentVariables.artifactoryUri.getOrExit
  private lazy val gitHubApiUser      = EnvironmentVariables.gitHubApiUser.getOrExit
  private lazy val gitHubApiToken     = EnvironmentVariables.gitHubApiToken.getOrExit
  private lazy val workspace          = EnvironmentVariables.workspace.getOrExit
  private lazy val javaVersion        = EnvironmentVariables.javaVersion.getOrExit

  private lazy implicit val system: ActorSystem    = ActorSystem()
  private lazy implicit val mat: ActorMaterializer = ActorMaterializer()

  private lazy val progressReporter = new ProgressReporter()
  private lazy val httpClient       = StandaloneAhcWSClient()
  private lazy val tarArchiver      = new TarArchiver()
  private lazy val slugArtifactName = SlugArtifactName(slugBuilderVersion)

  private lazy val slugBuilder = new SlugBuilder(
    progressReporter,
    new SlugChecker(httpClient, webstoreUri, slugArtifactName),
    new ArtifactFetcher(httpClient, artifactoryUri),
    new AppConfigBaseFetcher(httpClient, webstoreUri),
    new SlugFileAssembler(tarArchiver, new StartDockerScriptCreator()),
    new DockerImage(
      new BuildPackCloner(Git.cloneRepository(), gitHubApiUser, gitHubApiToken),
      new DockerImageRunner(workspace, webstoreUri, javaVersion, slugBuilderVersion, slugArtifactName))
  )

  def main(args: Array[String]): Unit = {

    val repositoryName = args.get("Repository name", atIdx = 0).flatMap(RepositoryName.create).getOrExit
    val releaseVersion = args.get("Release version", atIdx = 1).flatMap(ReleaseVersion.create).getOrExit

    Await
      .result(slugBuilder.create(repositoryName, releaseVersion).value, atMost = 2 minutes)
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
