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

import org.scalatest.WordSpec
import org.scalatest.prop.PropertyChecks
import uk.gov.hmrc.slugbuilder.generators.Generators._
import uk.gov.hmrc.slugbuilder.generators.Generators.Implicits._
import org.scalatest.Matchers._

class SlugBuilderSpec extends WordSpec with PropertyChecks {

  "create" should {

    "take a repository name and release version as arguments" in new Setup {
      forAll(nonEmptyStrings, releaseVersions) { (repoName, releaseVersion) =>
        slugBuilder.create(repoName, releaseVersion)
      }
    }

    "throw an exception if repository name is blank" in new Setup {
      intercept[IllegalArgumentException](slugBuilder.create(" ", releaseVersions.generateOne)).getMessage shouldBe "Blank repository name not allowed"
    }

    "throw an exception if release version is blank" in new Setup {
      intercept[IllegalArgumentException](slugBuilder.create(nonEmptyStrings.generateOne, " ")).getMessage shouldBe "Blank release version not allowed"
    }
  }

  private trait Setup {
    val slugBuilder = new SlugBuilder()
  }
}
