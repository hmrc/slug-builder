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

import cats.implicits._

object EnvironmentVariables {

  val all: Map[String, String]                    = sys.env
  val artifactoryUri: Either[String, String]      = findVariable("ARTIFACTORY_URI")
  val artifactoryUsername: Either[String, String] = findVariable("ARTIFACTORY_USERNAME")
  val artifactoryPassword: Either[String, String] = findVariable("ARTIFACTORY_PASSWORD")
  val slugRunnerVersion: Either[String, String]   = findVariable("SLUG_RUNNER_VERSION")
  val jdkFileName: Either[String, String]         = findVariable("JDK_FILE_NAME")
  val includeFiles: Option[String]                = all.get("INCLUDE_FILES")
  val slugRuntimeJavaOpts: Option[SlugRuntimeJavaOpts] =
    all.get("SLUG_RUNTIME_JAVA_OPTS").map(SlugRuntimeJavaOpts)

  private def findVariable(name: String): Either[String, String] = Either.fromOption(
    all.get(name),
    ifNone = s"No '$name' environment variable found"
  )
}
