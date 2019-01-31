/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.slugbuilder.tools

import java.nio.file.Path
import cats.implicits._

class TarArchiver(cliTools: CliTools) {

  def decompress(tgzFile: Path, outputDirectory: Path, preservePermissions: Boolean = true, stripLeadComponent:Boolean = false): Either[String, String] =
  {
    cliTools
      .run(Array("tar", if (preservePermissions) "-pxzf" else "-xzf", tgzFile.toString, "-C", outputDirectory.toString, if(stripLeadComponent) "--strip-components=1" else ""))
      .bimap(
        error => s"Couldn't decompress $tgzFile. Cause: $error",
        _ => s"Successfully decompressed $tgzFile"
      )}

  def decompressNotGZipped(tgzFile: Path, outputDirectory: Path, preservePermissions: Boolean = true, stripLeadComponent:Boolean = false): Either[String, String] =
  {
    cliTools
      .run(Array("tar", if (preservePermissions) "-pxf" else "-xf", tgzFile.toString, "-C", outputDirectory.toString, if(stripLeadComponent) "--strip-components=1" else ""))
      .bimap(
        error => s"Couldn't decompress $tgzFile. Cause: $error",
        _ => s"Successfully decompressed $tgzFile"
      )}


  def compress(tgzFile: Path, inputDirectory: Path): Either[String, String] =
    cliTools
      .run(Array("tar", "--exclude='./.git'", "-C", inputDirectory.toString, "-czf", tgzFile.toString, "."))
      .bimap(
        error => s"Couldn't compress $inputDirectory. Cause: $error",
        _ => s"Successfully compressed the $inputDirectory"
      )

}
