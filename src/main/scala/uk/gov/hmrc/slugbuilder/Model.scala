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

import cats.implicits._

case class RepositoryName private (value: String) {
  override val toString: String = value
}

object RepositoryName {
  def create(value: String): Either[String, RepositoryName] =
    Either.cond(
      test  = value.trim.nonEmpty,
      right = RepositoryName(value),
      left  = "Blank repository name not allowed"
    )
}

case class ReleaseVersion private (value: String) {
  override val toString: String = value
}

object ReleaseVersion {

  private val versionPattern = """(\d+)\.(\d+)\.(\d+)""".r

  def create(value: String): Either[String, ReleaseVersion] =
    Either
      .cond(
        test  = value.trim.nonEmpty,
        right = value,
        left  = "Blank release version not allowed"
      )
      .flatMap {
        case a @ versionPattern(majorVersion, minorVersion, patchVersion) => Right(ReleaseVersion(a))
        case _                                                            => Left(s"$value is not in valid release version format ('NNN.NNN.NNN')")
      }
}

case class ArtifactFileName(repositoryName: RepositoryName, releaseVersion: ReleaseVersion) {
  override def toString: String = s"$repositoryName-$releaseVersion.tgz"
}

case class AppConfigBaseFileName(repositoryName: RepositoryName) {
  override def toString: String = s"$repositoryName.conf"
}
