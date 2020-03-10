/*
 * Copyright 2020 HM Revenue & Customs
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

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.{Files, OpenOption, Path, Paths}
import cats.implicits._
import uk.gov.hmrc.slugbuilder.tools.CommandExecutor._
import scala.collection.JavaConversions._

class StartDockerScriptCreator(
  create: Path => Unit        = path => path.toFile.mkdir(),
  existCheck: Path => Boolean = path => path.toFile.exists(),
  move: (Path, Path) => Unit  = (file, directory) => Files.move(file, directory),
  copy: (Path, Path) => Unit  = (source, target) => Files.copy(source, target),
  createFile: (Path, Seq[String], Charset, OpenOption) => Unit = (file, content, charset, openOption) =>
    Files.write(file, content, charset, openOption)) {

  def ensureStartDockerExists(
                               workspace: Path,
                               slugDirectory: Path,
                               repositoryName: RepositoryName,
                               slugRuntimeJavaOpts: Option[SlugRuntimeJavaOpts]): Either[String, String] = {
    val startDockerFileInWorkspace = workspace resolve Paths.get("start-docker.sh")
    val startDockerFileInSlug      = slugDirectory resolve Paths.get("start-docker.sh")
    val appConfigBase              = Paths.get(AppConfigBaseFileName(repositoryName).toString)
    val confDirectory              = slugDirectory resolve "conf"
    val startDockerContent = Seq(
      "#!/usr/bin/env sh",
      s"SCRIPT=$$(find . -type f -name $repositoryName)"
    ) ++
      slugRuntimeJavaOpts.map(value =>  s"""export JAVA_OPTS="$$JAVA_OPTS $value"""") :+
      s"exec $$SCRIPT $$HMRC_CONFIG -Dconfig.file=${confDirectory.toFile.getName}/$appConfigBase"


    perform(existCheck(startDockerFileInWorkspace))
      .leftMap(exception => s"Couldn't check if $startDockerFileInWorkspace exists. Cause: ${exception.getMessage}")
      .flatMap { useProvidedStartDocker =>
        for {
          _ <- perform(create(confDirectory)) leftMap (exception =>
                s"Couldn't create conf directory at $confDirectory. Cause: ${exception.getMessage}")
          _ <- perform(move(appConfigBase, confDirectory resolve appConfigBase)) leftMap (exception =>
                s"Couldn't move $appConfigBase to $confDirectory. Cause: $exception")
          _ <- if (useProvidedStartDocker)
                perform(copy(startDockerFileInWorkspace, startDockerFileInSlug)) leftMap (exception =>
                  s"Couldn't copy the $startDockerFileInWorkspace script to the slug directory. Cause: $exception")
              else
                perform(createFile(startDockerFileInSlug, startDockerContent, UTF_8, CREATE_NEW)) leftMap (exception =>
                  s"Couldn't create $startDockerFileInSlug. Cause: $exception")
        } yield
          if (useProvidedStartDocker) "Successfully copied start-docker.sh from the workspace to the slug"
          else "Successfully created new start-docker.sh script"

      }
  }
}
