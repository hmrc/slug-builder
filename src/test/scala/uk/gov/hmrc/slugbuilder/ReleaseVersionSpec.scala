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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.slugbuilder.generators.Generators._

class ReleaseVersionSpec
  extends AnyWordSpec
     with Matchers
     with ScalaCheckDrivenPropertyChecks {

  "ReleaseVersion" should {

    "instantiate an object if version is in the 'NNN.NNN.NNN' format" in {
      forAll(releaseVersions) { releaseVersion =>
        ReleaseVersion.create(releaseVersion).map(_.toString) shouldBe Right(releaseVersion)
      }
    }

    "return an error if release version is blank" in {
      ReleaseVersion.create(" ") shouldBe Left("Blank release version not allowed")
    }

    "unknown.format" +: "1.a.0" +: "a.b.c" +: "1.2.3." +: "1.2.3a" +: Nil foreach { version =>
      s"throw an exception if '$version' version which is not in the 'NNN.NNN.NNN' format" in {
        ReleaseVersion.create(version) shouldBe Left(s"$version is not in valid release version format ('NNN.NNN.NNN')")
      }
    }
  }
}
