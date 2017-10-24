package nl.biopet.tools.vcfstats

import java.io.File
import java.nio.file.Files

import nl.biopet.test.BiopetTest
import nl.biopet.utils.sortAnyAny
import org.apache.commons.io.FileUtils
import org.testng.annotations.Test

class VcfStatsSparkTest extends BiopetTest {
  @Test
  def testNoArgs(): Unit = {
    intercept[IllegalArgumentException] {
      VcfStatsSpark.main(Array())
    }
  }

  @Test
  def testNoExistOutputDir(): Unit = {
    val tmp = Files.createTempDirectory("vcfStats")
    FileUtils.deleteDirectory(new File(tmp.toAbsolutePath.toString))
    val vcf = resourcePath("/chrQ.vcf.gz")
    val ref = resourcePath("/fake_chrQ.fa")

    intercept[IllegalArgumentException] {
      VcfStatsSpark.main(Array("-I", vcf, "-R", ref, "-o", tmp.toAbsolutePath.toString))
    }.getMessage shouldBe s"requirement failed: ${tmp.toAbsolutePath.toString} does not exist"
  }

  @Test
  def testOutputDirNoDir(): Unit = {
    val tmp = File.createTempFile("vcfstats.", ".vcfstats")
    val vcf = resourcePath("/chrQ.vcf.gz")
    val ref = resourcePath("/fake_chrQ.fa")

    intercept[IllegalArgumentException] {
      VcfStatsSpark.main(Array("-I", vcf, "-R", ref, "-o", tmp.getAbsolutePath))
    }.getMessage shouldBe s"requirement failed: ${tmp.getAbsolutePath} is not a directory"
  }

  @Test
  def testMain(): Unit = {
    val tmp = Files.createTempDirectory("vcfStats")
    val vcf = resourcePath("/chrQ.vcf.gz")
    val ref = resourcePath("/fake_chrQ.fa")

    noException should be thrownBy VcfStatsSpark.main(
      Array("-I", vcf, "-R", ref, "-o", tmp.toAbsolutePath.toString))
    noException should be thrownBy VcfStatsSpark.main(
      Array("-I", vcf, "-R", ref, "-o", tmp.toAbsolutePath.toString, "--allInfoTags"))
    noException should be thrownBy VcfStatsSpark.main(
      Array("-I",
        vcf,
        "-R",
        ref,
        "-o",
        tmp.toAbsolutePath.toString,
        "--allInfoTags",
        "--allGenotypeTags"))
    noException should be thrownBy VcfStatsSpark.main(
      Array("-I",
        vcf,
        "-R",
        ref,
        "-o",
        tmp.toAbsolutePath.toString,
        "--binSize",
        "50",
        "--writeBinStats"))

    // returns null when validation fails
    def validateArgs(array: Array[String]): Option[Args] = {
      val argsParser = new ArgsParser("nl/biopet/tools/vcfstats")
      argsParser.parse(array, Args())
    }

    val stderr1 = new java.io.ByteArrayOutputStream
    Console.withErr(stderr1) {
      validateArgs(
        Array("-I",
          vcf,
          "-R",
          ref,
          "-o",
          tmp.toAbsolutePath.toString,
          "--binSize",
          "50",
          "--writeBinStats",
          "--genotypeWiggle",
          "NonexistentThing")) shouldBe empty
    }

    val stderr2 = new java.io.ByteArrayOutputStream
    Console.withErr(stderr2) {
      validateArgs(
        Array("-I",
          vcf,
          "-R",
          ref,
          "-o",
          tmp.toAbsolutePath.toString,
          "--binSize",
          "50",
          "--writeBinStats",
          "--generalWiggle",
          "NonexistentThing")) shouldBe empty
    }

    val stderr3 = new java.io.ByteArrayOutputStream
    Console.withErr(stderr3) {
      validateArgs(Array("-R", ref, "-o", tmp.toAbsolutePath.toString)) shouldBe empty
    }
  }

  @Test
  def testEmptyVcf(): Unit = {
    val tmp = Files.createTempDirectory("vcfStats")
    val vcf = resourcePath("/empty.vcf.gz")
    val ref = resourcePath("/fake_chrQ.fa")

    noException should be thrownBy VcfStatsSpark.main(
      Array("-I", vcf, "-R", ref, "-o", tmp.toAbsolutePath.toString))
  }

  @Test
  def testSortAnyAny(): Unit = {
    //stub
    val one: Any = 1
    val two: Any = 2
    val text: Any = "hello"
    val text2: Any = "goodbye"

    sortAnyAny(one, two) shouldBe true
    sortAnyAny(two, one) shouldBe false
    sortAnyAny(text, text2) shouldBe false
    sortAnyAny(text2, text) shouldBe true
    sortAnyAny(one, text) shouldBe true
    sortAnyAny(text, one) shouldBe false
  }
}
