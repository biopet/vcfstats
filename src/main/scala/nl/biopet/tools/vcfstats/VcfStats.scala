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
  def argsParser = new ArgsParser(toolName)

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
}
