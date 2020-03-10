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

import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.slugbuilder.ArgParser.{Publish, Unpublish}
import scala.language.implicitConversions

class ArgParserSpec extends WordSpec with Matchers {

  "ArgParser" should {
    "create correct Publish config" in {
      ArgParser.parse("publish repoName 0.1.0") shouldBe Right(
        Publish(
          repositoryName = RepositoryName("repoName"),
          releaseVersion = ReleaseVersion("0.1.0")
        ))
    }

    "create correct Unpublish config" in {
      ArgParser.parse("unpublish repoName 0.1.0") shouldBe Right(
        Unpublish(
          repositoryName = RepositoryName("repoName"),
          releaseVersion = ReleaseVersion("0.1.0")
        ))
    }

    "fail if incorrect command is passed" in {
      ArgParser.parse("incorrect-command repoName 0.1.0") shouldBe Left(
        "Please supply 'publish' or 'unpublish' as the first argument"
      )
    }

    "fail if no repo name and release version name are passed" in {
      ArgParser.parse("publish") shouldBe Left(
        "'repository-name' required as argument 2."
      )
      ArgParser.parse("publish repoName") shouldBe Left(
        "'release-version' required as argument 3."
      )
    }
  }

  private implicit def stringToArray(string: String): Array[String] =
    Array(string.split(" "): _*)
}
