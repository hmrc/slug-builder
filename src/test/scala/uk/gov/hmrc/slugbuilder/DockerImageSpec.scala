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

import cats.data.EitherT._
import cats.implicits._
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.slugbuilder.generators.Generators.Implicits._
import uk.gov.hmrc.slugbuilder.generators.Generators.{releaseVersionGen, repositoryNameGen}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DockerImageSpec extends WordSpec with Matchers with MockFactory with ScalaFutures {
  "create" should {
    "return right if cloning the java buildpack and running the docker image are successful" in new Setup {
      (buildPackCloner.cloneRepo _)
        .expects()
        .returning(rightT[Future, String]("Buildpack cloned"))

      (dockerImageRunner
        .run(_: RepositoryName, _: ReleaseVersion))
        .expects(repositoryName, releaseVersion)
        .returning(rightT[Future, String]("Docker image ran successfully"))

      dockerImage.create(repositoryName, releaseVersion).value.futureValue shouldBe
        Right("Docker image ran successfully")
    }

    "return left if cloning fails" in new Setup {
      (buildPackCloner.cloneRepo _)
        .expects()
        .returning(leftT[Future, String]("Cloning buildpack failed"))

      dockerImage.create(repositoryName, releaseVersion).value.futureValue shouldBe
        Left("Cloning buildpack failed")
    }

    "return left if running the docker image fails" in new Setup {
      (buildPackCloner.cloneRepo _)
        .expects()
        .returning(rightT[Future, String]("Buildpack cloned"))

      (dockerImageRunner
        .run(_: RepositoryName, _: ReleaseVersion))
        .expects(repositoryName, releaseVersion)
        .returning(leftT[Future, String]("Running docker image failed"))

      dockerImage.create(repositoryName, releaseVersion).value.futureValue shouldBe
        Left("Running docker image failed")
    }
  }

  trait Setup {
    val buildPackCloner   = mock[BuildPackCloner]
    val dockerImageRunner = mock[DockerImageRunner]

    val repositoryName = repositoryNameGen.generateOne
    val releaseVersion = releaseVersionGen.generateOne

    val dockerImage = new DockerImage(buildPackCloner, dockerImageRunner)

  }
}
