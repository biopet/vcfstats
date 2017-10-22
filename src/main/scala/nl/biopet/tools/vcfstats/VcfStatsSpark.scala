package nl.biopet.tools.vcfstats

import java.io.File

import htsjdk.variant.variantcontext.VariantContext
import htsjdk.variant.vcf.{VCFFileReader, VCFHeader}
import nl.biopet.utils.ngs.intervals.{BedRecord, BedRecordList}
import nl.biopet.utils.spark
import nl.biopet.utils.tool.ToolCommand
import org.apache.spark.SparkContext
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

object VcfStatsSpark extends ToolCommand {

  def regions(cmdArgs: Args): List[BedRecord] = {
    (cmdArgs.intervals match {
      case Some(i) =>
        BedRecordList.fromFile(i).validateContigs(cmdArgs.referenceFile)
      case _ => BedRecordList.fromReference(cmdArgs.referenceFile)
    }).combineOverlap
      .scatter(cmdArgs.binSize,
        maxContigsInSingleJob = Some(cmdArgs.maxContigsInSingleJob)).flatten
  }


  def main(args: Array[String]): Unit = {
    val parser = new ArgsParser(toolName)
    val cmdArgs =
      parser.parse(args, Args()).getOrElse(throw new IllegalArgumentException)

    require(cmdArgs.outputDir.exists(), s"${cmdArgs.outputDir} does not exist")
    require(cmdArgs.outputDir.isDirectory, s"${cmdArgs.outputDir} is not a directory")

    logger.info("Start")

    val reader = new VCFFileReader(cmdArgs.inputFile, true)

    implicit val sc: SparkContext = spark.loadSparkContext(toolName, master = cmdArgs.sparkMaster, localThreads = cmdArgs.localThreads, sparkConfig = cmdArgs.sparkConfigValues)

    val cmdArgsBroadcast = sc.broadcast(cmdArgs)
    val header: Broadcast[VCFHeader] = sc.broadcast(reader.getFileHeader)

    logger.info("Start loading vcf file in memory")
    // Base records to to stats on
    sc.setJobGroup("Loading Vcf Records", "Loading Vcf Records")
    val vcfRecords = spark.vcf.loadRecords(cmdArgs.inputFile, regions(cmdArgsBroadcast.value))
    sc.clearJobGroup()
    logger.info("Vcf file in memory, submitting jobs to calculate stats")

    sc.setJobGroup("Sample compare", "Sample compare")
    val sampleCompareFutures = sampleCompare(vcfRecords, header, cmdArgsBroadcast)
    sc.clearJobGroup()

    sc.setJobGroup("General stats", "General stats")
    val generalStatsFutures = generalStats(vcfRecords, header, cmdArgsBroadcast)
    sc.clearJobGroup()

    Await.result(Future.sequence(sampleCompareFutures ::: generalStatsFutures), Duration.Inf)

    logger.info("Done")
  }

  def contigDir(outputDir: File, contig: String) = new File(outputDir, "contig" + File.separator + contig)

  def generalStats(vcfRecords: RDD[VariantContext],
                   header: Broadcast[VCFHeader],
                   cmdArgs: Broadcast[Args]): List[Future[Unit]] = {
    val generalContig = spark.vcf.generalStats(vcfRecords)
    val generalTotal = generalContig.map("total" -> _._2).reduceByKey(_ += _)
      .foreachAsync(_._2.writeToTsv(new File(cmdArgs.value.outputDir, "general.tsv")))
    val genotypeContig = spark.vcf.genotypeStats(vcfRecords, header)
    val genotypeTotal = genotypeContig.map("total" -> _._2).reduceByKey(_ += _)
      .foreachAsync(_._2.writeToTsv(new File(cmdArgs.value.outputDir, "genotype.tsv")))

    val contigFutures = if (!cmdArgs.value.notWriteContigStats) {
      List(generalContig.foreachAsync { case (contig,stats) =>
        val dir = contigDir(cmdArgs.value.outputDir, contig)
        dir.mkdirs()
        stats.writeToTsv(new File(dir, "general.tsv"))
      }, genotypeContig.foreachAsync { case (contig, stats) =>
        val dir = contigDir(cmdArgs.value.outputDir, contig)
        dir.mkdirs()
        stats.writeToTsv(new File(dir, "genotype.tsv"))
      })
    } else Nil
    generalTotal :: genotypeTotal :: contigFutures
  }

  def sampleCompare(vcfRecords: RDD[VariantContext], header: Broadcast[VCFHeader], cmdArgs: Broadcast[Args]): List[Future[Unit]] = {
    val contigCompare = spark.vcf.sampleCompare(vcfRecords, header, cmdArgs.value.sampleToSampleMinDepth).cache()
    val sampleCompareDir = new File(cmdArgs.value.outputDir, "sample_compare")
    sampleCompareDir.mkdir()
    val contigFuture = if (!cmdArgs.value.notWriteContigStats) {
      Some(contigCompare.foreachAsync { case (contig, compare) =>
        val dir = new File(contigDir(cmdArgs.value.outputDir, contig), "sample_compare")
        dir.mkdirs()
        compare.writeAllFiles(dir)
      })
    } else None

    val totalFuture = contigCompare.map("total" -> _._2)
      .reduceByKey(_ += _)
      .foreachAsync(_._2.writeAllFiles(sampleCompareDir))

    contigCompare.unpersist()

    totalFuture :: contigFuture.toList
  }
}

