/*
 * Copyright (c) 2014 Biopet
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package nl.biopet.tools.vcfstats

import java.io.File
import java.nio.file.Files

import nl.biopet.utils.ngs.vcf.GenotypeStats
import nl.biopet.utils.test.tools.ToolTest
import nl.biopet.utils.tool.ToolCommand
import org.apache.commons.io.FileUtils
import org.testng.annotations.{DataProvider, Test}

import scala.io.Source

class VcfStatsTest extends ToolTest[Args] {

  def toolCommand: ToolCommand[Args] = VcfStats

  System.setProperty("spark.driver.host", "localhost")

  @DataProvider(name = "executables")
  def executables(): Array[Array[AnyRef]] = {
    Array(
      Array(VcfStatsSpark),
      Array(VcfStats)
    )
  }

  @DataProvider(name = "executablesModule")
  def executablesModule(): Array[Array[AnyRef]] = {
    Array(
      Array(VcfStatsSpark, Boolean.box(true)),
      Array(VcfStatsSpark, Boolean.box(false)),
      Array(VcfStats, Boolean.box(false))
    )
  }

  private def asModulesArgs(flag: Boolean): Array[String] = {
    if (flag) Array("--executeModulesAsJobs")
    else Array()
  }

  def testOutput(outputDir: File,
                 skipGeneral: Boolean = false,
                 skipGenotype: Boolean = false,
                 skipSampleDistributions: Boolean = false,
                 skipSampleCompare: Boolean = false,
                 skipContigStats: Boolean = false,
                 contigs: List[String] = Nil): Unit = {
    def testDir(dir: File): Unit = {
      dir should exist
      val general = new File(dir, "general.tsv")
      if (skipGeneral) general shouldNot exist
      else general should exist
      val genotype = new File(dir, "genotype.tsv")
      if (skipGenotype) genotype shouldNot exist
      else genotype should exist

      val sampleDistribution = new File(dir, "sample_distributions")
      if (skipSampleDistributions) sampleDistribution shouldNot exist
      else {
        sampleDistribution should exist
        GenotypeStats.values.foreach { x =>
          new File(sampleDistribution, s"$x.tsv") should exist
          new File(sampleDistribution, s"$x.aggregate.tsv") should exist
        }
      }

      val sampleCompareDir = new File(dir, "sample_compare")
      if (skipSampleCompare) sampleCompareDir shouldNot exist
      else {
        sampleCompareDir should exist
        new File(sampleCompareDir, "allele.abs.tsv") should exist
        new File(sampleCompareDir, "allele.rel.tsv") should exist
        new File(sampleCompareDir, "genotype.abs.tsv") should exist
        new File(sampleCompareDir, "genotype.rel.tsv") should exist
      }
    }

    testDir(outputDir)

    val contigDir = new File(outputDir, "contigs")
    if (skipContigStats) {
      contigDir shouldNot exist
      contigDir.list().toList.sorted shouldBe contigs.sorted
      contigs.foreach(c => testDir(new File(contigDir, c)))
    } else contigDir should exist
  }

  @Test(dataProvider = "executables")
  def testNoArgs(executable: ToolCommand[Args]): Unit = {
    intercept[IllegalArgumentException] {
      executable.main(Array())
    }
  }

  @Test(dataProvider = "executables")
  def testNoExistOutputDir(executable: ToolCommand[Args]): Unit = {
    val tmp = Files.createTempDirectory("vcfStats")
    FileUtils.deleteDirectory(new File(tmp.toAbsolutePath.toString))
    val vcf = resourcePath("/chrQ.vcf.gz")
    val ref = resourcePath("/fake_chrQ.fa")

    intercept[IllegalArgumentException] {
      executable.main(
        Array("-I", vcf, "-R", ref, "-o", tmp.toAbsolutePath.toString))
    }.getMessage shouldBe s"requirement failed: ${tmp.toAbsolutePath.toString} does not exist"
  }

  @Test(dataProvider = "executablesModule")
  def testOutputDirNoDir(executable: ToolCommand[Args],
                         modulesAsJobs: Boolean): Unit = {
    val tmp = File.createTempFile("vcfstats.", ".vcfstats")
    val vcf = resourcePath("/chrQ.vcf.gz")
    val ref = resourcePath("/fake_chrQ.fa")

    intercept[IllegalArgumentException] {
      executable.main(
        Array("-I", vcf, "-R", ref, "-o", tmp.getAbsolutePath) ++ asModulesArgs(
          modulesAsJobs))
    }.getMessage shouldBe s"requirement failed: ${tmp.getAbsolutePath} is not a directory"
  }

  @Test(dataProvider = "executablesModule")
  def testMultiBins(executable: ToolCommand[Args],
                    modulesAsJobs: Boolean): Unit = {
    val tmp = Files.createTempDirectory("vcfStats")
    val vcf = resourcePath("/chrQ.vcf.gz")
    val ref = resourcePath("/fake_chrQ.fa")

    noException should be thrownBy executable.main(
      Array("-I",
            vcf,
            "-R",
            ref,
            "-o",
            tmp.toAbsolutePath.toString,
            "--binSize",
            "1000") ++ asModulesArgs(modulesAsJobs))
    testOutput(tmp.toFile, contigs = "chrQ" :: Nil)
  }

  @Test(dataProvider = "executablesModule")
  def testMain(executable: ToolCommand[Args], modulesAsJobs: Boolean): Unit = {
    val tmp = Files.createTempDirectory("vcfStats")
    val vcf = resourcePath("/chrQ.vcf.gz")
    val ref = resourcePath("/fake_chrQ.fa")

    noException should be thrownBy executable.main(Array(
      "-I",
      vcf,
      "-R",
      ref,
      "-o",
      tmp.toAbsolutePath.toString) ++ asModulesArgs(modulesAsJobs))
    testOutput(tmp.toFile, contigs = "chrQ" :: Nil)
  }

  @Test(dataProvider = "executablesModule")
  def testSkips(executable: ToolCommand[Args], modulesAsJobs: Boolean): Unit = {
    val tmp = Files.createTempDirectory("vcfStats")
    val vcf = resourcePath("/chrQ.vcf.gz")
    val ref = resourcePath("/fake_chrQ.fa")

    noException should be thrownBy executable.main(
      Array("-I",
            vcf,
            "-R",
            ref,
            "-o",
            tmp.toAbsolutePath.toString,
            "--skipGeneral",
            "--skipGenotype",
            "--skipSampleCompare",
            "--skipSampleDistributions") ++ asModulesArgs(modulesAsJobs))
    testOutput(tmp.toFile,
               contigs = "chrQ" :: Nil,
               skipGeneral = true,
               skipGenotype = true,
               skipSampleDistributions = true,
               skipSampleCompare = true)
  }

  @Test(dataProvider = "executables")
  def testMasterArg(executable: ToolCommand[Args]): Unit = {
    val tmp = Files.createTempDirectory("vcfStats")
    val vcf = resourcePath("/chrQ.vcf.gz")
    val ref = resourcePath("/fake_chrQ.fa")

    noException should be thrownBy executable.main(
      Array("-I",
            vcf,
            "-R",
            ref,
            "-o",
            tmp.toAbsolutePath.toString,
            "--sparkMaster",
            "local[1]"))
    testOutput(tmp.toFile, contigs = "chrQ" :: Nil)
  }
  @Test(dataProvider = "executables")
  def testEmptyVcf(executable: ToolCommand[Args]): Unit = {
    val tmp = Files.createTempDirectory("vcfStats")
    val vcf = resourcePath("/empty.vcf.gz")
    val ref = resourcePath("/fake_chrQ.fa")

    noException should be thrownBy executable.main(
      Array("-I", vcf, "-R", ref, "-o", tmp.toAbsolutePath.toString))
    testOutput(tmp.toFile, contigs = "chrQ" :: Nil)
  }

  @Test(dataProvider = "executablesModule")
  def testInfoField(executable: ToolCommand[Args],
                    modulesAsJobs: Boolean): Unit = {
    val tmp = Files.createTempDirectory("vcfStats").toFile
    val vcf = resourcePath("/multi.vcf.gz")
    val ref = resourcePath("/fake_chrQ.fa")

    noException should be thrownBy executable.main(
      Array("-I",
            vcf,
            "-R",
            ref,
            "-o",
            tmp.getAbsolutePath,
            "--infoTag",
            "DP:All") ++ asModulesArgs(modulesAsJobs))
    testOutput(tmp, contigs = "chrQ" :: Nil)

    val tsv = new File(tmp, "info.DP_All.tsv")
    tsv should exist
    val lines = Source.fromFile(tsv).getLines().toList
    lines.headOption shouldBe Some("value\tcount")
    lines.tail shouldBe List(
      "1\t3",
      "2\t2",
      "3\t1"
    )
  }

  @Test(dataProvider = "executablesModule")
  def testGenotypeField(executable: ToolCommand[Args],
                        modulesAsJobs: Boolean): Unit = {
    val tmp = Files.createTempDirectory("vcfStats").toFile
    val vcf = resourcePath("/multi.vcf.gz")
    val ref = resourcePath("/fake_chrQ.fa")

    noException should be thrownBy executable.main(
      Array("-I",
            vcf,
            "-R",
            ref,
            "-o",
            tmp.getAbsolutePath,
            "--genotypeTag",
            "DP:All") ++ asModulesArgs(modulesAsJobs))
    testOutput(tmp, contigs = "chrQ" :: Nil)

    val tsv = new File(tmp, "genotype.DP_All.tsv")
    tsv should exist
    val lines = Source.fromFile(tsv).getLines().toList
    lines.headOption shouldBe Some("Sample\tSample_3\tSample_2\tSample_1")
    lines.tail shouldBe List(
      "1\t0\t1\t1",
      "5\t3\t2\t2"
    )
  }
}
