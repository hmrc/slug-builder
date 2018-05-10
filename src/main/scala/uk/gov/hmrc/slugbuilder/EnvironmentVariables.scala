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

class EnvironmentVariables(progressReporter: ProgressReporter) {

  val webstoreUri: Either[String, String]        = findVariable("WEBSTORE_URI")
  val artifactoryUri: Either[String, String]     = findVariable("ARTIFACTORY_URI")
  val slugBuilderVersion: Either[String, String] = findVariable("SLUG_BUILDER_VERSION")

  private def findVariable(name: String): Either[String, String] = Either.fromOption(
    sys.env.get(name),
    ifNone = s"No '$name' environment variable found"
  )
}
