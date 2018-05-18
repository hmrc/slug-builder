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

package uk.gov.hmrc.slugbuilder.functions

import uk.gov.hmrc.slugbuilder.{ReleaseVersion, RepositoryName}

case class SlugArtifactName(slugBuilderVersion: String) extends ((RepositoryName, ReleaseVersion) => String) {
  override def apply(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): String =
    s"${repositoryName}_${releaseVersion}_$slugBuilderVersion.tgz"
}
