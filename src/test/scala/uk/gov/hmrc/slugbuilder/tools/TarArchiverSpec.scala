package uk.gov.hmrc.slugbuilder.tools

import java.io.FileOutputStream
import java.nio.file.{Files, Path, Paths}
import java.util.zip.GZIPOutputStream

import org.scalatest.Matchers._
import org.scalatest.WordSpec

import scala.collection.JavaConversions._

class TarArchiverSpec extends WordSpec {

  "archiver" should {

    "allow to compress to tar and decompress from tgz" in new Setup {
      val tar = Paths.get("compressed.tar")
      archiver.compressToTar(tar.toFile.getAbsolutePath, folder.toFile.listFiles())

      val tgz = Paths.get("compressed.tgz")
      compress(inputFile = tar, outputFile = tgz)

      val uncompressed = Paths.get("uncompressed")
      new TarArchiver().decompress(tgz.toFile.getAbsolutePath, uncompressed.toFile)

      verifyFolderStructureExtracted(to = uncompressed)

      delete(folder, tar, tgz, uncompressed)
    }
  }

  private trait Setup {

    val archiver = new TarArchiver()

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

  private def compress(inputFile: Path, outputFile: Path): Unit = {
    val bos  = new FileOutputStream(outputFile.toFile)
    val gzip = new GZIPOutputStream(bos)
    gzip.write(Files.readAllBytes(inputFile))
    gzip.close()
    bos.close()
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
