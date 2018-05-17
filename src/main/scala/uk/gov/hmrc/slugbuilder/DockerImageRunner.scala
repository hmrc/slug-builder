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

import java.nio.file.{Files, Paths}
import cats.data.EitherT
import cats.implicits._
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.core.DockerClientBuilder
import uk.gov.hmrc.slugbuilder.tools.CommandExecutor.perform
import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class DockerImageRunner(workspaceUri: String, webstoreUri: String, javaVersion: String, slugBuilderVersion: String) {

  def run(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): EitherT[Future, String, String] =
    EitherT
      .fromEither[Future](perform(runDocker(repositoryName, releaseVersion)))
      .leftMap(exception =>
        s"Couldn't create the docker image at ${slugUrl(repositoryName, releaseVersion)}. Cause: ${exception.getMessage}")
      .map(_ => s"Image created at ${slugUrl(repositoryName, releaseVersion)}")

  private def runDocker(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): Unit = {
    val dockerClient = DockerClientBuilder.getInstance.build
    val containerResponse =
      dockerClient
        .createContainerCmd(s"hmrc/slugbuilder:$slugBuilderVersion")
        .withName(s"$repositoryName-$releaseVersion")
        .withVolumes(new Volume(s"$workspaceUri/bp:/tmp/bp:ro"))
        .withAttachStdin(true)
        .withAttachStdout(true)
        .withAttachStderr(true)
        .withStdinOpen(true)
        .withEnv(
          Seq(
            s"JAVA_VERSION=jdk-$javaVersion",
            "BUILDPACK_URL=/tmp/bp/",
            "LC_ALL=C",
            s"JAVA_DOWNLOAD_URI=$webstoreUri/java/",
            "STACK=cedar"))
        .withCmd(slugUrl(repositoryName, releaseVersion))
        .exec()

    println(s"container response $containerResponse")

    dockerClient
      .copyArchiveToContainerCmd(containerResponse.getId)
      .withTarInputStream(Files.newInputStream(Paths.get(s"$repositoryName-$releaseVersion.tar")))
      .exec()
  }

  private def slugUrl(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): String =
    s"$webstoreUri/slugs/$repositoryName/${repositoryName}_${releaseVersion}_$slugBuilderVersion.tgz"

}
