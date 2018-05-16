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

import java.io.{FileInputStream, FileOutputStream}
import java.nio.file.Files._
import java.nio.file.{Path, Paths}
import java.util.stream.{Stream => JavaStream}

import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveInputStream, TarArchiveOutputStream}
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.utils.IOUtils

import scala.collection.JavaConversions._

class TarArchiver {

  def decompress(tgzFile: Path, outputDirectory: Path): Unit = {
    val inputStream = archiveInputStream(tgzFile)
    try {
      Stream
        .continually(inputStream.getNextTarEntry)
        .takeWhile(_ != null)
        .map(entry =>
          if (!entry.isDirectory) {
            val currentFile = outputDirectory resolve Paths.get(entry.getName).normalize()
            if (!exists(currentFile.getParent)) currentFile.getParent.toFile.mkdirs()
            IOUtils.copy(inputStream, new FileOutputStream(currentFile.toFile))
        })
        .force
    } finally inputStream.close()
  }

  private def archiveInputStream(tgzFile: Path) =
    Option(new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(tgzFile.toFile))))
      .getOrElse {
        throw new RuntimeException("Tar archive reader cannot be created")
      }

  def tar(destination: Path, files: JavaStream[Path]): Unit = {
    val outputStream = archiveOutputStream(destination)
    try {
      files.iterator() foreach (file => addToArchive(outputStream, file, Paths.get("")))
    } finally outputStream.close()
  }

  private def archiveOutputStream(destination: Path) =
    Option(new TarArchiveOutputStream(new FileOutputStream(destination.toFile)))
      .map { tarOutputStream =>
        // TAR has an 8 gig file limit by default, this gets around that
        tarOutputStream.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR)

        // TAR originally didn't support long file names, so enable the support for it
        tarOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
        tarOutputStream.setAddPaxHeadersForNonAsciiNames(true)
        tarOutputStream
      }
      .getOrElse {
        throw new RuntimeException("Tar archive writer cannot be created")
      }

  private def addToArchive(outputStream: TarArchiveOutputStream, file: Path, dir: Path): Unit = {
    val entry = dir resolve file.toFile.getName
    if (isDirectory(file)) {
      val children = list(file).iterator()
      for (child <- children) {
        addToArchive(outputStream, child, entry)
      }
    } else {
      outputStream.putArchiveEntry(new TarArchiveEntry(file.toFile, entry.toString))
      try {
        val in = new FileInputStream(file.toFile)
        try IOUtils.copy(in, outputStream)
        finally if (in != null) in.close()
      } finally outputStream.closeArchiveEntry()
    }
  }
}
