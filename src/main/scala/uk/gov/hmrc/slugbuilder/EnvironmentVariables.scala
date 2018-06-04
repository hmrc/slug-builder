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

object EnvironmentVariables {

  val webstoreUri: Either[String, String]        = findVariable("WEBSTORE_URI")
  val artifactoryUri: Either[String, String]     = findVariable("ARTIFACTORY_URI")
  val workspace: Either[String, String]          = findVariable("WORKSPACE")
  val slugBuilderVersion: Either[String, String] = findVariable("SLUG_BUILDER_VERSION")
  val slugDirectory: Either[String, String]      = findVariable("SLUG_DIR")
  val gitHubApiUser: Either[String, String]      = findVariable("GITHUB_API_USER")
  val gitHubApiToken: Either[String, String]     = findVariable("GITHUB_API_TOKEN")
  val javaVersion: Either[String, String]        = findVariable("JAVA_VERSION")
  val javaDownloadUri: Either[String, String]    = findVariable("JAVA_DOWNLOAD_URI")
  val javaVendor: Either[String, String]         = findVariable("JAVA_VENDOR")

  private def findVariable(name: String): Either[String, String] = Either.fromOption(
    sys.env.get(name),
    ifNone = s"No '$name' environment variable found"
  )
}
