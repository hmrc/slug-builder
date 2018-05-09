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

import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.prop.PropertyChecks
import uk.gov.hmrc.slugbuilder.generators.Generators._

class ReleaseVersionSpec extends WordSpec with PropertyChecks {

  "ReleaseVersion" should {

    "instantiate an object if version is in the 'NNN.NNN.NNN' format" in {
      forAll(releaseVersions) { releaseVersion =>
        ReleaseVersion(releaseVersion).toString shouldBe releaseVersion
      }
    }

    "provide major, minor and patch versions of the given release version" in {
      val releaseVersion = ReleaseVersion("2.3.0")

      releaseVersion.major shouldBe "2"
      releaseVersion.minor shouldBe "3"
      releaseVersion.patch shouldBe "0"
    }

    "throw an exception if release version is blank" in {
      intercept[IllegalArgumentException](ReleaseVersion(" ")).getMessage shouldBe "Blank release version not allowed"
    }

    "unknown.format" +: "1.a.0" +: "a.b.c" +: "1.2.3.4" +: "1.2.3a" +: Nil foreach { version =>
      s"throw an exception if '$version' version which is not in the 'NNN.NNN.NNN' format" in {
        intercept[IllegalArgumentException](ReleaseVersion(version)).getMessage shouldBe s"$version is not in 'NNN.NNN.NNN' format"
      }
    }
  }
}
