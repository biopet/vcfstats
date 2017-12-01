package nl.biopet.tools.vcfstats

import java.io.File

object Readme {
  def main(args: Array[String]): Unit = {
    val readme: File = new File(args(0))
    VcfStats.generateReadme(readme)
  }
}
