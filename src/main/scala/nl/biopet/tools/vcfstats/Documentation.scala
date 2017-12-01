package nl.biopet.tools.vcfstats

import java.io.File

object Documentation {
  def main(args: Array[String]): Unit = {
    val docsDir: File = new File(args(0))
    val version: String = args(1)
    val redirect: Boolean = args(2).toBoolean
    VcfStats.generateDocumentation(
      outputDirectory = docsDir,
      version = version,
      redirect= redirect
    )
  }
}
