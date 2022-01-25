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

package uk.gov.hmrc.slugbuilder.tools

import java.nio.charset.Charset
import java.nio.file.{Files, OpenOption, Path}
import java.nio.file.attribute.PosixFilePermission

import scala.jdk.CollectionConverters._

class FileUtils {
  def createDir(dir: Path): Unit =
    if (Files.exists(dir)) () else dir.toFile.mkdir()

  def setPermissions(file: Path, permissions: Set[PosixFilePermission]): Unit =
    Files.setPosixFilePermissions(file, permissions.asJava)

  def createFile(file: Path, content: String, charset: Charset, openOption: OpenOption): Unit =
    Files.write(file, Seq(content).asJava, charset, openOption)

  def copyFile(source: Path, targetDirectory: Path): Unit =
    Files.copy(source, targetDirectory.resolve(source.getFileName))
}
