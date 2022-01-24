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

import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.prop.PropertyChecks
import uk.gov.hmrc.slugbuilder.generators.Generators._

class ModelSpec extends WordSpec with PropertyChecks {

  "apply" should {
    "return a String comprised of repositoryName and releaseVersion" in {
      forAll(repositoryNameGen, releaseVersionGen) { (repositoryName, releaseVersion) =>
        ArtifactFileName(repositoryName, releaseVersion).toString shouldBe s"$repositoryName-$releaseVersion.tgz"
      }
    }
  }
}

class AppConfigBaseFileNameSpec extends WordSpec with PropertyChecks {

  "apply" should {
    "return a String comprised of repositoryName" in {
      forAll(repositoryNameGen) { repositoryName =>
        AppConfigBaseFileName(repositoryName).toString shouldBe s"$repositoryName.conf"
      }
    }
  }
}
