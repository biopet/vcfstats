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

import htsjdk.variant.variantcontext.VariantContext
import htsjdk.variant.vcf.{VCFFileReader, VCFHeader}
import nl.biopet.utils.ngs.intervals.BedRecord
import nl.biopet.utils.ngs.vcf.{
  GeneralStats,
  GenotypeFieldCounts,
  GenotypeStats,
  InfoFieldCounts,
  SampleCompare,
  SampleDistributions,
  VcfField
}
import nl.biopet.utils.spark
import nl.biopet.utils.tool.ToolCommand
import org.apache.spark.{FutureAction, SparkConf, SparkContext}
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

/**
  * This tool can generate metrics from a vcf file.
  * This version of the tool run on Apache Spark
  * @author Peter van 't Hof
  */
object VcfStatsSpark extends ToolCommand[Args] {

  def emptyArgs: Args = Args()

  def argsParser = new ArgsParser(this)

  /** Main entry point from the commandline */
  def main(args: Array[String]): Unit = {
    val cmdArgs = cmdArrayToArgs(args)

    require(cmdArgs.outputDir.exists(), s"${cmdArgs.outputDir} does not exist")
    require(cmdArgs.outputDir.isDirectory,
            s"${cmdArgs.outputDir} is not a directory")

    mainFromArgs(cmdArgs)
  }

  /** API entry point */
  def mainFromArgs(cmdArgs: Args): Unit = {
    logger.info("Start")

    if (!cmdArgs.notWriteContigStats)
      new File(cmdArgs.outputDir, "contigs").mkdir()

    val reader = new VCFFileReader(cmdArgs.inputFile, true)

    val sparkConf: SparkConf =
      new SparkConf(true).setMaster(cmdArgs.sparkMaster.getOrElse("local[1]"))
    implicit val sparkSession: SparkSession =
      SparkSession.builder().config(sparkConf).getOrCreate()
    implicit val sc: SparkContext = sparkSession.sparkContext

    try {
      val cmdArgsBroadcast = sc.broadcast(cmdArgs)
      val header: Broadcast[VCFHeader] = sc.broadcast(reader.getFileHeader)
      val regions: Broadcast[List[BedRecord]] =
        sc.broadcast(VcfStats.regions(cmdArgs))

      val shortContigs = sc.broadcast(
        regions.value
          .groupBy(_.chr)
          .filter(_._2.length == 1)
          .values
          .flatten
          .toList)
      val longContigs = regions.value
        .groupBy(_.chr)
        .filter(_._2.length != 1)
        .map(x => x._1 -> sc.broadcast(x._2))
        .toList

      val contigsProcess = processContig(shortContigs,
                                         cmdArgsBroadcast,
                                         header,
                                         None) ::
        (for ((contig, regions) <- longContigs) yield {
        processContig(regions, cmdArgsBroadcast, header, Some(contig))
      })

      val generalStats = totalGeneralStats(
        contigsProcess.flatMap(_.generalStats),
        cmdArgsBroadcast)
      val genotypeStats = totalGenotypeStats(
        contigsProcess.flatMap(_.genotypeStats),
        cmdArgsBroadcast,
        header)
      val sampleDistributions = totalSampleDistributions(
        contigsProcess.flatMap(_.sampleDistributions),
        cmdArgsBroadcast)
      val sampleCompare = totalSampleCompare(
        contigsProcess.flatMap(_.sampleCompare),
        cmdArgsBroadcast,
        header)
      val infoFieldCounts = totalContigInfoFieldCounts(
        cmdArgs.infoTags
          .map(x => x -> contigsProcess.flatMap(_.infoFieldCounts.get(x)))
          .toMap,
        header,
        cmdArgsBroadcast)
      val genotypeFieldCounts = totalContigGenotypeFieldCounts(
        cmdArgs.genotypeTags
          .map(x => x -> contigsProcess.flatMap(_.genotypeFieldCounts.get(x)))
          .toMap,
        header,
        cmdArgsBroadcast)

      logger.info("Done submitting jobs")

      Await.result(Future.sequence(contigsProcess.map(_.contigFuture)),
                   Duration.Inf)
      Await.result(Future.sequence(
                     List(generalStats,
                          genotypeStats,
                          sampleDistributions,
                          sampleCompare,
                          infoFieldCounts,
                          genotypeFieldCounts).flatten),
                   Duration.Inf)
    } finally {
      sc.stop()
    }
    logger.info("Done")
  }

  /** This is a class to collect results from 1 chunk */
  case class ChunkResult(
      contigFuture: Future[Any],
      generalStats: Option[RDD[(String, GeneralStats)]],
      genotypeStats: Option[RDD[(String, GenotypeStats)]],
      sampleDistributions: Option[RDD[(String, SampleDistributions)]],
      sampleCompare: Option[RDD[(String, SampleCompare)]],
      infoFieldCounts: Map[VcfField, RDD[(String, InfoFieldCounts)]],
      genotypeFieldCounts: Map[VcfField, RDD[(String, GenotypeFieldCounts)]])

  /** This method will execute a list of regions. */
  def processContig(
      regions: Broadcast[List[BedRecord]],
      cmdArgs: Broadcast[Args],
      header: Broadcast[VCFHeader],
      contig: Option[String])(implicit sc: SparkContext): ChunkResult = {
    val prefixMessage = contig match {
      case Some(c) => s"Contig $c:"
      case _ => "Small contigs:"
    }
    logger.info(s"$prefixMessage Start reading vcf file")
    val vcfRecords = loadVcfFile(cmdArgs, regions, prefixMessage)
    logger.info(
      s"$prefixMessage Vcf file in memory, submitting jobs to calculate stats")

    val sampleCompare =
      contigSampleCompare(vcfRecords, header, cmdArgs, regions, prefixMessage)
    val generalStats =
      contigGeneralStats(vcfRecords, cmdArgs, regions, prefixMessage)
    val genotypeStats =
      contigGenotypeStats(vcfRecords, header, cmdArgs, regions, prefixMessage)
    val sampleDistribution =
      contigSampleDistributions(vcfRecords, cmdArgs, regions, prefixMessage)

    logger.info("Submitting infoFieldCounts jobs")
    val infoCounts = contigInfoFieldCounts(vcfRecords,
                                           header,
                                           cmdArgs,
                                           regions,
                                           prefixMessage)

    logger.info("Submitting genotypeFieldCounts jobs")
    val genotypeCounts = contigGenotypeFieldCounts(vcfRecords,
                                                   header,
                                                   cmdArgs,
                                                   regions,
                                                   prefixMessage)

    vcfRecords.unpersist()

    val contigOutputFuture = Future.sequence(
      sampleCompare._1.toList ::: generalStats._1.toList ::: sampleDistribution._1.toList :::
        genotypeStats._1.toList ::: infoCounts
        .flatMap(_._2._1)
        .toList ::: genotypeCounts.flatMap(_._2._1).toList)

    ChunkResult(
      contigOutputFuture,
      generalStats._2,
      genotypeStats._2,
      sampleDistribution._2,
      sampleCompare._2,
      infoCounts.map(x => x._1 -> x._2._2),
      genotypeCounts.map(x => x._1 -> x._2._2)
    )
  }

  /** Returns the contig dir of a given contig */
  def contigDir(outputDir: File, contig: String) =
    new File(outputDir, "contigs" + File.separator + contig)

  /** This method will load a list of regions into memory */
  def loadVcfFile(cmdArgs: Broadcast[Args],
                  regions: Broadcast[List[BedRecord]],
                  prefixMessage: String)(
      implicit sc: SparkContext): RDD[VariantContext] = {
    sc.setJobGroup(s"$prefixMessage Loading Vcf Records",
                   s"$prefixMessage Loading Vcf Records")
    val vcfRecords = spark.vcf
      .loadRecords(cmdArgs.value.inputFile,
                   regions,
                   cmdArgs.value.binSize,
                   cached = true)
    vcfRecords.countAsync()
    sc.clearJobGroup()
    vcfRecords
  }

  /** This will generate contigs stats and write this if this is not disabled */
  def contigGeneralStats(vcfRecords: RDD[VariantContext],
                         cmdArgs: Broadcast[Args],
                         regions: Broadcast[List[BedRecord]],
                         prefixMessage: String)
    : (Option[FutureAction[Unit]], Option[RDD[(String, GeneralStats)]]) = {
    if (cmdArgs.value.skipGeneral) (None, None)
    else {
      vcfRecords.sparkContext.setJobGroup(s"$prefixMessage General stats",
                                          s"$prefixMessage General stats")
      val generalContig = spark.vcf.generalStats(vcfRecords, regions).cache()

      val contigFutures = if (!cmdArgs.value.notWriteContigStats) {
        Some(generalContig.foreachAsync {
          case (contig, stats) =>
            val dir = contigDir(cmdArgs.value.outputDir, contig)
            dir.mkdirs()
            stats.writeToTsv(new File(dir, "general.tsv"))
        })
      } else None
      vcfRecords.sparkContext.clearJobGroup()
      (contigFutures, Some(generalContig))
    }
  }

  /** This will combine contigs stats and write the output file */
  def totalGeneralStats(generalContig: List[RDD[(String, GeneralStats)]],
                        cmdArgs: Broadcast[Args]): Option[Future[Unit]] = {
    if (generalContig.nonEmpty) {
      val sc = generalContig.head.context
      sc.setJobGroup("Total General stats", "Total General stats")
      val emptyRdd = sc.parallelize(List("total" -> new GeneralStats()))
      Some(
        sc.union(emptyRdd :: generalContig)
          .map { case (_, stats) => "total" -> stats }
          .reduceByKey(_ += _)
          .foreachAsync {
            case (_, stats) =>
              stats.writeToTsv(
                new File(cmdArgs.value.outputDir, "general.tsv"))
          })
    } else None
  }

  /** This will generate contigs stats and write this if this is not disabled */
  def contigGenotypeStats(vcfRecords: RDD[VariantContext],
                          header: Broadcast[VCFHeader],
                          cmdArgs: Broadcast[Args],
                          regions: Broadcast[List[BedRecord]],
                          prefixMessage: String)
    : (Option[FutureAction[Unit]], Option[RDD[(String, GenotypeStats)]]) = {
    if (cmdArgs.value.skipGeneral) (None, None)
    else {
      vcfRecords.sparkContext.setJobGroup(s"$prefixMessage Genotype stats",
                                          s"$prefixMessage Genotype stats")
      val genotypeContig =
        spark.vcf.genotypeStats(vcfRecords, header, regions).cache()
      val contigFutures = if (!cmdArgs.value.notWriteContigStats) {
        Some(genotypeContig.foreachAsync {
          case (contig, stats) =>
            val dir = contigDir(cmdArgs.value.outputDir, contig)
            dir.mkdirs()
            stats.writeToTsv(new File(dir, "genotype.tsv"))
        })
      } else None
      vcfRecords.sparkContext.clearJobGroup()
      (contigFutures, Some(genotypeContig))
    }
  }

  /** This will combine contigs stats and write the output file */
  def totalGenotypeStats(
      contigs: List[RDD[(String, GenotypeStats)]],
      cmdArgs: Broadcast[Args],
      header: Broadcast[VCFHeader]): Option[Future[Unit]] = {
    if (contigs.nonEmpty) {
      val sc = contigs.head.context
      sc.setJobGroup("Total Genotype stats", "Total Genotype stats")
      val emptyRdd =
        sc.parallelize(List("total" -> new GenotypeStats(header.value)))
      Some(
        sc.union(emptyRdd :: contigs)
          .map { case (_, stats) => "total" -> stats }
          .reduceByKey(_ += _)
          .foreachAsync {
            case (_, stats) =>
              stats.writeToTsv(
                new File(cmdArgs.value.outputDir, "genotype.tsv"))
          })
    } else None
  }

  /** This will generate contigs stats and write this if this is not disabled */
  def contigSampleDistributions(
      vcfRecords: RDD[VariantContext],
      cmdArgs: Broadcast[Args],
      regions: Broadcast[List[BedRecord]],
      prefixMessage: String): (Option[FutureAction[Unit]],
                               Option[RDD[(String, SampleDistributions)]]) = {
    if (cmdArgs.value.skipSampleDistributions) (None, None)
    else {
      vcfRecords.context.setJobGroup(s"$prefixMessage Sample Distributions",
                                     s"$prefixMessage Sample Distributions")
      val contigs = spark.vcf.sampleDistributions(vcfRecords, regions).cache()
      val contigFutures = if (!cmdArgs.value.notWriteContigStats) {
        Some(contigs.foreachAsync {
          case (contig, stats) =>
            val dir = new File(contigDir(cmdArgs.value.outputDir, contig),
                               "sample_distributions")
            dir.mkdirs()
            stats.writeToDir(dir)
        })
      } else None
      vcfRecords.sparkContext.clearJobGroup()
      (contigFutures, Some(contigs))
    }
  }

  /** This will combine contigs stats and write the output file */
  def totalSampleDistributions(
      contigs: List[RDD[(String, SampleDistributions)]],
      cmdArgs: Broadcast[Args]): Option[Future[Unit]] = {
    if (contigs.nonEmpty) {
      val sc = contigs.head.context
      sc.setJobGroup("Total Sample Distributions",
                     "Total Sample Distributions")
      val emptyRdd = sc.parallelize(List("total" -> new SampleDistributions))
      Some(
        sc.union(emptyRdd :: contigs)
          .map { case (_, stats) => "total" -> stats }
          .reduceByKey(_ += _)
          .foreachAsync {
            case (_, stats) =>
              val dir =
                new File(cmdArgs.value.outputDir, "sample_distributions")
              dir.mkdir()
              stats.writeToDir(dir)
          })
    } else None
  }

  /** This will generate contigs stats and write this if this is not disabled */
  def contigSampleCompare(vcfRecords: RDD[VariantContext],
                          header: Broadcast[VCFHeader],
                          cmdArgs: Broadcast[Args],
                          regions: Broadcast[List[BedRecord]],
                          prefixMessage: String)
    : (Option[FutureAction[Unit]], Option[RDD[(String, SampleCompare)]]) = {
    if (cmdArgs.value.skipSampleCompare) (None, None)
    else {
      vcfRecords.sparkContext.setJobGroup(s"$prefixMessage Sample compare",
                                          s"$prefixMessage Sample compare")
      val contigCompare = spark.vcf
        .sampleCompare(vcfRecords,
                       header,
                       regions,
                       cmdArgs.value.sampleToSampleMinDepth)
        .cache()
      val contigFuture = if (!cmdArgs.value.notWriteContigStats) {
        Some(contigCompare.foreachAsync {
          case (contig, compare) =>
            val dir = new File(contigDir(cmdArgs.value.outputDir, contig),
                               "sample_compare")
            dir.mkdirs()
            compare.writeAllFiles(dir)
        })
      } else None
      vcfRecords.sparkContext.clearJobGroup()
      (contigFuture, Some(contigCompare))
    }
  }

  /** This will combine contigs stats and write the output file */
  def totalSampleCompare(
      contigs: List[RDD[(String, SampleCompare)]],
      cmdArgs: Broadcast[Args],
      header: Broadcast[VCFHeader]): Option[Future[Unit]] = {
    if (contigs.nonEmpty) {
      val sc = contigs.head.context
      sc.setJobGroup("Total Sample Compare", "Total Sample Compare")
      val emptyRdd =
        sc.parallelize(List("total" -> new SampleCompare(header.value)))
      Some(
        sc.union(emptyRdd :: contigs)
          .map { case (_, stats) => "total" -> stats }
          .reduceByKey(_ += _)
          .foreachAsync {
            case (_, stats) =>
              val dir = new File(cmdArgs.value.outputDir, "sample_compare")
              dir.mkdir()
              stats.writeAllFiles(dir)
          })
    } else None

  }

  /** This will generate contigs stats and write this if this is not disabled */
  def contigInfoFieldCounts(vcfRecords: RDD[VariantContext],
                            header: Broadcast[VCFHeader],
                            cmdArgs: Broadcast[Args],
                            regions: Broadcast[List[BedRecord]],
                            prefixMessage: String)
    : Map[VcfField, (Option[Future[Unit]], RDD[(String, InfoFieldCounts)])] = {
    (for (tag <- cmdArgs.value.infoTags)
      yield
        tag -> {
          vcfRecords.sparkContext.setJobGroup(
            s"$prefixMessage Info field counts: $tag",
            s"$prefixMessage Info field counts: $tag")
          val tagBroadcast = vcfRecords.sparkContext.broadcast(tag)
          val contigCounts = spark.vcf
            .infoFieldCounts(vcfRecords, header, tagBroadcast, regions)
            .cache()
          val contigFuture = if (!cmdArgs.value.notWriteContigStats) {
            Some(contigCounts.foreachAsync {
              case (contig, counts) =>
                val dir = contigDir(cmdArgs.value.outputDir, contig)
                dir.mkdirs()
                val file = new File(dir, s"info.${tagBroadcast.value}.tsv")
                counts.writeHistogram(file)
            })
          } else None

          vcfRecords.sparkContext.clearJobGroup()
          (contigFuture, contigCounts)
        }).toMap
  }

  /** This will combine contigs stats and write the output file */
  def totalContigInfoFieldCounts(
      contigs: Map[VcfField, List[RDD[(String, InfoFieldCounts)]]],
      header: Broadcast[VCFHeader],
      cmdArgs: Broadcast[Args]): Option[Future[Any]] = {
    val futures: List[Future[Unit]] = (for ((field, counts) <- contigs) yield {
      if (counts.isEmpty) None
      else {
        val sc = counts.head.context
        sc.setJobGroup(s"Total Info field counts: $field",
                       s"Total Info field counts: $field")
        val emptyRdd = sc.parallelize(
          List(
            "total" -> new InfoFieldCounts(
              header.value.getInfoHeaderLine(field.key),
              field.method)))
        Some(
          sc.union(emptyRdd :: counts)
            .map { case (_, stats) => "total" -> stats }
            .reduceByKey(_ += _)
            .foreachAsync {
              case (_, stats) =>
                stats.writeHistogram(
                  new File(cmdArgs.value.outputDir, s"info.$field.tsv"))
            })
      }
    }).flatten.toList
    if (futures.nonEmpty) Some(Future.sequence(futures))
    else None
  }

  /** This will generate contigs stats and write this if this is not disabled */
  def contigGenotypeFieldCounts(
      vcfRecords: RDD[VariantContext],
      header: Broadcast[VCFHeader],
      cmdArgs: Broadcast[Args],
      regions: Broadcast[List[BedRecord]],
      prefixMessage: String): Map[VcfField,
                                  (Option[Future[Unit]],
                                   RDD[(String, GenotypeFieldCounts)])] = {
    (for (tag <- cmdArgs.value.genotypeTags)
      yield
        tag -> {
          vcfRecords.sparkContext.setJobGroup(
            s"$prefixMessage Genotype field counts: $tag",
            s"$prefixMessage Genotype field counts: $tag")
          val tagBroadcast = vcfRecords.sparkContext.broadcast(tag)
          val contigCounts = spark.vcf
            .genotypeFieldCounts(vcfRecords, header, tagBroadcast, regions)
            .cache()
          val contigFuture = if (!cmdArgs.value.notWriteContigStats) {
            Some(contigCounts.foreachAsync {
              case (contig, counts) =>
                val dir = contigDir(cmdArgs.value.outputDir, contig)
                dir.mkdirs()
                val file = new File(dir, s"genotype.${tagBroadcast.value}.tsv")
                counts.writeToFile(file)
            })
          } else None

          vcfRecords.sparkContext.clearJobGroup()
          (contigFuture, contigCounts)
        }).toMap
  }

  /** This will combine contigs stats and write the output file */
  def totalContigGenotypeFieldCounts(
      contigs: Map[VcfField, List[RDD[(String, GenotypeFieldCounts)]]],
      header: Broadcast[VCFHeader],
      cmdArgs: Broadcast[Args]): Option[Future[Any]] = {
    val futures: List[Future[Unit]] = (for ((field, counts) <- contigs) yield {
      if (counts.isEmpty) None
      else {
        val sc = counts.head.context
        sc.setJobGroup(s"Total Genotype field counts: $field",
                       s"Total Genotype field counts: $field")
        val emptyRdd = sc.parallelize(
          List(
            "total" -> new GenotypeFieldCounts(
              header.value,
              header.value.getFormatHeaderLine(field.key),
              field.method)))
        Some(
          sc.union(emptyRdd :: counts)
            .map { case (_, stats) => "total" -> stats }
            .reduceByKey(_ += _)
            .foreachAsync {
              case (_, stats) =>
                stats.writeToFile(
                  new File(cmdArgs.value.outputDir, s"genotype.$field.tsv"))
            })
      }
    }).flatten.toList
    if (futures.nonEmpty) Some(Future.sequence(futures))
    else None
  }

  def descriptionText: String = VcfStats.descriptionText

  def manualText: String = VcfStats.manualText

  def exampleText: String = VcfStats.exampleText
}
