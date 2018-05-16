package uk.gov.hmrc.slugbuilder.tools

import java.io.{File, FileInputStream, FileOutputStream}

import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveInputStream, TarArchiveOutputStream}
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.utils.IOUtils

class TarArchiver {

  def decompress(name: String, out: File): Unit = {
    val inputStream = archiveInputStream(name)
    try {
      Stream.continually(inputStream.getNextTarEntry).takeWhile(_ != null).map( entry =>
        if (!entry.isDirectory) {
          val currentFile = new File(out, entry.getName)
          val parent = currentFile.getParentFile
          if (!parent.exists) parent.mkdirs
          IOUtils.copy(inputStream, new FileOutputStream(currentFile))
        }
      ).force
    } finally inputStream.close()
  }

  private def archiveInputStream(name: String) =
    Option(new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(name))))
      .getOrElse {
        throw new RuntimeException("Tar archive reader cannot be created")
      }

  def compressToTar(destination: String, files: Seq[File]): Unit = {
    val outputStream = archiveOutputStream(destination)
    try {
      files foreach (file => addToArchive(outputStream, file, "."))
    } finally outputStream.close()
  }

  private def archiveOutputStream(name: String) =
    Option(new TarArchiveOutputStream(new FileOutputStream(name)))
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

  private def addToArchive(outputStream: TarArchiveOutputStream, file: File, dir: String): Unit = {
    val entry = dir + File.separator + file.getName
    if (file.isFile) {
      outputStream.putArchiveEntry(new TarArchiveEntry(file, entry))
      try {
        val in = new FileInputStream(file)
        try IOUtils.copy(in, outputStream)
        finally if (in != null) in.close()
      }
      outputStream.closeArchiveEntry()
    } else if (file.isDirectory) {
      val children = file.listFiles
      if (children != null) for (child <- children) {
        addToArchive(outputStream, child, entry)
      }
    } else throw new RuntimeException(s"${file.getName} is not supported for adding to a TAR")
  }
}
