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

package uk.gov.hmrc.slugbuilder

import java.nio.file.Path
import java.nio.file.{Path, Paths}

import cats.implicits._

object ArgParser {

  sealed trait Config {
    val repositoryName: RepositoryName
    val releaseVersion: ReleaseVersion
  }

  case class Publish(repositoryName: RepositoryName, releaseVersion: ReleaseVersion, additionalBinaries:Seq[AdditionalBinary] = Seq()) extends Config

  case class Unpublish(repositoryName: RepositoryName, releaseVersion: ReleaseVersion) extends Config

  def parse(args: Array[String]): Either[String, Config] = {
    for {
      command <- args.get("command-name", atIdx = 0).flatMap {
        case c@("publish" | "unpublish") => Right(c)
        case _ => Left("Please supply 'publish' or 'unpublish' as the first argument")
      }
      repoName <- args.get("repository-name", atIdx = 1).flatMap(RepositoryName.create)
      version <- args.get("release-version", atIdx = 2).flatMap(ReleaseVersion.create)
      additionalBinaries <- args.collectRemaining(3).right.flatMap(args => pairArgs(args))
    } yield
      (command: @unchecked) match {
        case "publish" => Publish(repoName, version, additionalBinaries)
        case "unpublish" => Unpublish(repoName, version)
      }
  }

  def pairArgs(args:Seq[String]):Either[String, List[AdditionalBinary]] = {
    if(args.size % 2 == 0) {
      Right(args.grouped(2).map { case Seq(a, b) => AdditionalBinary(a, Paths.get(b)) }.toList)
    }else{
      Left("Argument is missing a pair")
    }
  }

  private implicit class ArgsOps(args: Array[String]) {

    def get(argName: String, atIdx: Int): Either[String, String] =
      if (atIdx >= args.length)
        Left(s"'$argName' required as argument ${atIdx + 1}.")
      else
        Right(args(atIdx))

    def collectRemaining(atIdx:Int):Either[String, Seq[String]] = {
      if(atIdx > args.length){
        Right(Seq())
      }else{
        Right(args.takeRight(args.length - atIdx))
      }
    }
  }
}
