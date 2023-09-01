/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.slugbuilder.connectors

import cats.implicits._
import uk.gov.hmrc.slugbuilder.{AppConfigBaseFileName, RepositoryName}

class GithubConnector(
  fileDownloader     : FileDownloader,
  githubApiToken     : String
) {
  def downloadAppConfigBase(repositoryName: RepositoryName): Either[String, String] = {
    val fileUrl = FileUrl(s"https://raw.githubusercontent.com/hmrc/app-config-base/main/$repositoryName.conf")
    fileDownloader
      .download(
        fileUrl,
        DestinationFileName(AppConfigBaseFileName(repositoryName).toString),
        headers = Map("Authorization" -> s"Token $githubApiToken")
      )
      .bimap(
        downloadError => s"app-config-base couldn't be downloaded from $fileUrl. Cause: $downloadError",
        _             => s"Successfully downloaded app-config-base from $fileUrl"
      )
  }
}
