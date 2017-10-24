package nl.biopet.tools.vcfstats

import java.io.File

import htsjdk.variant.variantcontext.VariantContext
import htsjdk.variant.vcf.{VCFFileReader, VCFHeader}
import nl.biopet.utils.spark
import nl.biopet.utils.tool.ToolCommand
import org.apache.spark.SparkContext
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object VcfStatsSpark extends ToolCommand {

  def main(args: Array[String]): Unit = {
    val parser = new ArgsParser(toolName)
    val cmdArgs =
      parser.parse(args, Args()).getOrElse(throw new IllegalArgumentException)

    require(cmdArgs.outputDir.exists(), s"${cmdArgs.outputDir} does not exist")
    require(cmdArgs.outputDir.isDirectory,
            s"${cmdArgs.outputDir} is not a directory")

    mainFromArgs(cmdArgs)
  }

  def mainFromArgs(cmdArgs: Args): Unit = {
    logger.info("Start")

    val reader = new VCFFileReader(cmdArgs.inputFile, true)

    implicit val sc: SparkContext = spark.loadSparkContext(
      toolName,
      master = cmdArgs.sparkMaster,
      localThreads = cmdArgs.localThreads,
      sparkConfig = cmdArgs.sparkConfigValues)

    try {
      val cmdArgsBroadcast = sc.broadcast(cmdArgs)
      val header: Broadcast[VCFHeader] = sc.broadcast(reader.getFileHeader)

      logger.info("Start loading vcf file in memory")
      // Base records to to stats on
      val vcfRecords = loadVcfFile(cmdArgsBroadcast)
      logger.info("Vcf file in memory, submitting jobs to calculate stats")

      val sampleCompareFutures =
        sampleCompare(vcfRecords, header, cmdArgsBroadcast)

      val generalStatsFutures =
        generalStats(vcfRecords, header, cmdArgsBroadcast)
      val genotypeStatsFutures =
        genotypeStats(vcfRecords, header, cmdArgsBroadcast)

      Await.result(
        Future.sequence(
          sampleCompareFutures ::: generalStatsFutures ::: genotypeStatsFutures),
        Duration.Inf)
    } finally {
      sc.stop()
    }
    logger.info("Done")
  }

  def contigDir(outputDir: File, contig: String) =
    new File(outputDir, "contig" + File.separator + contig)

  def loadVcfFile(cmdArgs: Broadcast[Args])(
      implicit sc: SparkContext): RDD[VariantContext] = {
    sc.setJobGroup("Loading Vcf Records", "Loading Vcf Records")
    val vcfRecords = spark.vcf
      .loadRecords(cmdArgs.value.inputFile, VcfStats.regions(cmdArgs.value))
    sc.clearJobGroup()
    vcfRecords
  }

  def generalStats(vcfRecords: RDD[VariantContext],
                   header: Broadcast[VCFHeader],
                   cmdArgs: Broadcast[Args]): List[Future[Unit]] = {
    vcfRecords.sparkContext.setJobGroup("General stats", "General stats")
    val generalContig = spark.vcf.generalStats(vcfRecords)
    val generalTotal = generalContig
      .map("total" -> _._2)
      .reduceByKey(_ += _)
      .foreachAsync(
        _._2.writeToTsv(new File(cmdArgs.value.outputDir, "general.tsv")))

    val contigFutures = if (!cmdArgs.value.notWriteContigStats) {
      List(generalContig.foreachAsync {
        case (contig, stats) =>
          val dir = contigDir(cmdArgs.value.outputDir, contig)
          dir.mkdirs()
          stats.writeToTsv(new File(dir, "general.tsv"))
      })
    } else Nil
    vcfRecords.sparkContext.clearJobGroup()
    generalTotal :: contigFutures
  }

  def genotypeStats(vcfRecords: RDD[VariantContext],
                    header: Broadcast[VCFHeader],
                    cmdArgs: Broadcast[Args]): List[Future[Unit]] = {
    vcfRecords.sparkContext.setJobGroup("Genotype stats", "Genotype stats")
    val genotypeContig = spark.vcf.genotypeStats(vcfRecords, header)
    val genotypeTotal = genotypeContig
      .map("total" -> _._2)
      .reduceByKey(_ += _)
      .foreachAsync(
        _._2.writeToTsv(new File(cmdArgs.value.outputDir, "genotype.tsv")))

    val contigFutures = if (!cmdArgs.value.notWriteContigStats) {
      List(genotypeContig.foreachAsync {
        case (contig, stats) =>
          val dir = contigDir(cmdArgs.value.outputDir, contig)
          dir.mkdirs()
          stats.writeToTsv(new File(dir, "genotype.tsv"))
      })
    } else Nil
    vcfRecords.sparkContext.clearJobGroup()
    genotypeTotal :: contigFutures
  }

  def sampleCompare(vcfRecords: RDD[VariantContext],
                    header: Broadcast[VCFHeader],
                    cmdArgs: Broadcast[Args]): List[Future[Unit]] = {
    vcfRecords.sparkContext.setJobGroup("Sample compare", "Sample compare")
    val contigCompare = spark.vcf
      .sampleCompare(vcfRecords, header, cmdArgs.value.sampleToSampleMinDepth)
      .cache()
    val sampleCompareDir = new File(cmdArgs.value.outputDir, "sample_compare")
    sampleCompareDir.mkdir()
    val contigFuture = if (!cmdArgs.value.notWriteContigStats) {
      Some(contigCompare.foreachAsync {
        case (contig, compare) =>
          val dir = new File(contigDir(cmdArgs.value.outputDir, contig),
                             "sample_compare")
          dir.mkdirs()
          compare.writeAllFiles(dir)
      })
    } else None

    val totalFuture = contigCompare
      .map("total" -> _._2)
      .reduceByKey(_ += _)
      .foreachAsync(_._2.writeAllFiles(sampleCompareDir))

    contigCompare.unpersist()

    vcfRecords.sparkContext.clearJobGroup()
    totalFuture :: contigFuture.toList
  }
}
