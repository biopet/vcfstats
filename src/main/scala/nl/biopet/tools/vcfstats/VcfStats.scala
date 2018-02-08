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

import htsjdk.variant.vcf.VCFFileReader
import nl.biopet.utils.ngs.intervals.{BedRecord, BedRecordList}
import nl.biopet.utils.tool.ToolCommand

import scala.collection.JavaConversions._

/**
  * This tool can generate metrics from a vcf file
  * @author Peter van 't Hof
  */
object VcfStats extends ToolCommand[Args] {

  def emptyArgs: Args = Args()
  def argsParser = new ArgsParser(this)

  /** Main entry point from the command line */
  def main(args: Array[String]): Unit = {
    val cmdArgs = cmdArrayToArgs(args)

    require(cmdArgs.outputDir.exists(), s"${cmdArgs.outputDir} does not exist")
    require(cmdArgs.outputDir.isDirectory,
            s"${cmdArgs.outputDir} is not a directory")

    if (cmdArgs.sparkMaster.isDefined) VcfStatsSpark.mainFromArgs(cmdArgs)
    else mainFromArgs(cmdArgs)
  }

  /** API entry point */
  def mainFromArgs(cmdArgs: Args): Unit = {
    val reader = new VCFFileReader(cmdArgs.inputFile, true)
    val header = reader.getFileHeader
    val totalStats = StatsTotal.empty(header, cmdArgs)

    for ((contig, regions) <- regions(cmdArgs).groupBy(_.chr)) {
      val contigStats = StatsTotal.empty(header, cmdArgs)
      val contigDir =
        new File(cmdArgs.outputDir, "contigs" + File.separator + contig)
      contigDir.mkdirs()
      for (region <- regions;
           record <- reader.query(region.chr, region.start + 1, region.end)) {
        contigStats.addRecord(record, cmdArgs)
      }
      contigStats.writeStats(contigDir)
      totalStats += contigStats
    }

    totalStats.writeStats(cmdArgs.outputDir)
  }

  /** creates regions to analyse */
  def regions(cmdArgs: Args): List[BedRecord] = {
    (cmdArgs.intervals match {
      case Some(i) =>
        BedRecordList.fromFile(i).validateContigs(cmdArgs.referenceFile)
      case _ => BedRecordList.fromReference(cmdArgs.referenceFile)
    }).combineOverlap
      .scatter(cmdArgs.binSize,
               maxContigsInSingleJob = Some(cmdArgs.maxContigsInSingleJob))
      .flatten
  }

  def descriptionText: String =
    """
      |Vcfstats is a tool that can generate metrics from a vcf file.
      |
      | - General stats (default, can be disabled)
      | - Genotype stats (default, can be disabled)
      | - Sample compare (default, can be disabled)
      | - Sample distributions (default, can be disabled)
      | - Field histograms
      |
      |This tool can run locally single threaded but also on a Apache Spark cluster.
    """.stripMargin

  def manualText: String =
    """
      |Almost all stats are default but can be disabled if required. When having a lot of samples the sample compare step if very intensive.
      |
      |The reference fasta should have at least a dict file next to it.
      |
      |For the field histograms there are some methods to choose from. This method will be used to choose what value to report in the histogram if there multiple values. Some method require the value to be a Int or Double. If this is not the case an exception is thrown.
      |
      | - Min, takes minimal number (Int or double required)
      | - Max, takes maximum number (Int or double required)
      | - Avg, calculates the average (Int or double required)
      | - All, returns all values as separated values
      | - Unique, returns all unique values as separated values
      |
    """.stripMargin

  def exampleText: String =
    s"""
      |Default vcfstats run:
      |${example("-I",
                 "<input_vcf>",
                 "-o",
                 "<output_dir>",
                 "-R",
                 "<reference_fasta>")}
      |
      |Run only specific regions:
      |${example("-I",
                 "<input_vcf>",
                 "-o",
                 "<output_dir>",
                 "-R",
                 "<reference_fasta>",
                 "--intervals",
                 "<bed_file>")}
      |
      |Create a histogram of a info field, for the methods see the manual section:
      |${example("-I",
                 "<input_vcf>",
                 "-o",
                 "<output_dir>",
                 "-R",
                 "<reference_fasta>",
                 "--infoTag",
                 "<tag_id>:All")}
      |
      |Create a histogram of a info field, for the methods see the manual section:
      |${example("-I",
                 "<input_vcf>",
                 "-o",
                 "<output_dir>",
                 "-R",
                 "<reference_fasta>",
                 "--genotypeTag",
                 "<tag_id>:All")}
      |
      |Run vcfstats on spark. The arg `sparkExecutorMemory` can be changed what is suitable for your cluster, the rest of the spark configs can be changed with the arg `sparkConfigValue`:
      |${example("-I",
                 "<input_vcf>",
                 "-o",
                 "<output_dir>",
                 "-R",
                 "<reference_fasta>",
                 "--sparkMaster",
                 "<spark_master>",
                 "--sparkExecutorMemory",
                 "10g")}
    """.stripMargin

}
