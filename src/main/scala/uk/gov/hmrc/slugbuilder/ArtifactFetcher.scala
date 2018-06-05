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
import uk.gov.hmrc.slugbuilder.functions.ArtifactFileName
import uk.gov.hmrc.slugbuilder.tools.{DestinationFileName, FileDownloader, FileUrl}

class ArtifactFetcher(fileDownloader: FileDownloader, artifactoryUri: String) {

  def download(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): Either[String, String] = {

    val fileUrl = FileUrl(
      s"$artifactoryUri/uk/gov/hmrc/${repositoryName}_2.11/$releaseVersion/${repositoryName}_2.11-$releaseVersion.tgz"
    )

    fileDownloader
      .download(fileUrl, DestinationFileName(ArtifactFileName(repositoryName, releaseVersion)))
      .bimap(
        downloadError => s"Artifact couldn't be downloaded from $fileUrl. Cause: $downloadError",
        _ => s"Artifact successfully downloaded from $fileUrl"
      )
  }
}
