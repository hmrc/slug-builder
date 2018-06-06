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

package uk.gov.hmrc.slugbuilder.tools

import java.nio.file.{Files, Path, Paths}
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import scala.collection.JavaConversions._

class TarArchiverSpec extends WordSpec {

  "archiver" should {

    "allow to compress to tar and decompress from tgz" in new Setup {
      val tgz = Paths.get("compressed.tgz")
      archiver.compress(tgz, folder)

      val uncompressed = Paths.get("uncompressed")

      uncompressed.toFile.mkdir()

      archiver.decompress(tgz, uncompressed)

      verifyFolderStructureExtracted(to = uncompressed)

      delete(folder, tgz, uncompressed)
    }
  }

  private trait Setup {

    val progressReporter = new ProgressReporterStub()
    val cliTools         = new CliTools(progressReporter)
    val archiver         = new TarArchiver(cliTools)

    val folder = Paths.get("folder")
    folder.toFile.mkdir()

    val folderFile: Path = Paths.get("file.txt")
    Files.write(folder resolve folderFile, Seq("root level"))

    val subfolder = folder resolve "subfolder"
    subfolder.toFile.mkdir()

    val subfolderFile: Path = Paths.get("file.txt")
    Files.write(subfolder resolve subfolderFile, Seq("subfolder level"))

    def verifyFolderStructureExtracted(to: Path) = {
      Files.readAllLines(to resolve folderFile).mkString shouldBe "root level"

      assert((to resolve subfolder.getFileName).toFile.exists(), s"$subfolder should exist")
      Files
        .readAllLines(to resolve subfolder.getFileName resolve subfolderFile)
        .mkString shouldBe "subfolder level"
    }
  }

  private def delete(files: Path*): Unit = files.foreach { file =>
    if (file.toFile.isDirectory)
      Option(file.toFile.listFiles)
        .map(_.toList)
        .getOrElse(Nil)
        .foreach(fileInDir => delete(Paths.get(fileInDir.toURI)))
    file.toFile.delete
  }
}
