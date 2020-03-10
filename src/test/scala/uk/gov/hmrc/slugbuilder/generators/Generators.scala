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

package uk.gov.hmrc.slugbuilder.generators

import org.scalacheck.Gen
import uk.gov.hmrc.slugbuilder.{ReleaseVersion, RepositoryName}

object Generators {

  object Implicits {
    implicit class GenOps[T](generator: Gen[T]) {
      lazy val generateOne: T = generator.sample.getOrElse(generateOne)
    }
  }

  def strings(minLength: Int, maxLength: Int): Gen[String] =
    for {
      length <- Gen.chooseNum(minLength, maxLength)
      chars  <- Gen.listOfN(length, Gen.alphaNumChar)
    } yield chars.mkString

  val nonEmptyStrings: Gen[String] = strings(minLength = 1, maxLength = 50)

  val releaseVersions: Gen[String] = for {
    majorVersion <- Gen.chooseNum(0, 100)
    minorVersion <- Gen.chooseNum(0, 100)
    patchVersion <- Gen.chooseNum(0, 100)
  } yield s"$majorVersion.$minorVersion.$patchVersion"

  val repositoryNameGen: Gen[RepositoryName] = nonEmptyStrings.map(RepositoryName.apply)
  val releaseVersionGen: Gen[ReleaseVersion] = releaseVersions.map(ReleaseVersion.apply)

  val allHttpStatusCodes: Seq[Int] = (200 to 208) ++: (300 to 308) ++: (400 to 431) ++: (500 to 511)

  def httpStatusCodes(excluding: Int*): Gen[Int] = Gen.oneOf(allHttpStatusCodes filterNot excluding.contains)
}
