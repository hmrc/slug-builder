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

package uk.gov.hmrc.slugbuilder

object ArgParser {

  sealed trait Config {
    val repositoryName: RepositoryName
    val releaseVersion: ReleaseVersion
  }

  case class Publish(repositoryName: RepositoryName, releaseVersion: ReleaseVersion) extends Config

  case class Unpublish(repositoryName: RepositoryName, releaseVersion: ReleaseVersion) extends Config

  def parse(args: Array[String]): Either[String, Config] =
    for {
      command <- args.get("command-name", atIdx = 0).flatMap {
                  case c @ ("publish" | "unpublish") => Right(c)
                  case _                             => Left("Please supply 'publish' or 'unpublish' as the first argument")
                }
      repoName <- args.get("repository-name", atIdx = 1).flatMap(RepositoryName.create)
      version  <- args.get("release-version", atIdx = 2).flatMap(ReleaseVersion.create)
    } yield
      (command: @unchecked) match {
        case "publish"   => Publish(repoName, version)
        case "unpublish" => Unpublish(repoName, version)
      }

  private implicit class ArgsOps(args: Array[String]) {

    def get(argName: String, atIdx: Int): Either[String, String] =
      if (atIdx >= args.length)
        Left(s"'$argName' required as argument ${atIdx + 1}.")
      else
        Right(args(atIdx))
  }
}
