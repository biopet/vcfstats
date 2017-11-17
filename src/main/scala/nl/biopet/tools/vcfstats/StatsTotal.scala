package nl.biopet.tools.vcfstats

import java.io.File

import htsjdk.variant.variantcontext.VariantContext
import htsjdk.variant.vcf.VCFHeader
import nl.biopet.utils.ngs.vcf
import nl.biopet.utils.ngs.vcf.{
  GenotypeFieldCounts,
  InfoFieldCounts,
  SampleCompare,
  SampleDistributions
}

case class StatsTotal(general: Option[vcf.GeneralStats],
                      genotype: Option[vcf.GenotypeStats],
                      sampleDistributions: Option[SampleDistributions],
                      sampleCompare: Option[SampleCompare],
                      infoFields: Map[vcf.VcfField, InfoFieldCounts],
                      genotypeFields: Map[vcf.VcfField, GenotypeFieldCounts]) {

  /** This method will add a single variantcontext to the stats object */
  def addRecord(record: VariantContext, cmdArgs: Args): Unit = {
    general.foreach(_.addRecord(record))
    genotype.foreach(_.addRecord(record))
    sampleDistributions.foreach(_.addRecord(record))
    sampleCompare.foreach(_.addRecord(record, cmdArgs.sampleToSampleMinDepth))
    infoFields.foreach(_._2.addRecord(record))
    genotypeFields.foreach(_._2.addRecord(record))
  }

  /** This combines stats classes into this */
  def +=(other: StatsTotal): StatsTotal = {
    require(other.general.isDefined == this.general.isDefined)
    require(other.genotype.isDefined == this.genotype.isDefined)
    require(
      other.sampleDistributions.isDefined == this.sampleDistributions.isDefined)
    require(other.sampleCompare.isDefined == this.sampleCompare.isDefined)
    other.general.foreach(o => this.general.foreach(_ += o))
    other.genotype.foreach(o => this.genotype.foreach(_ += o))
    other.sampleDistributions.foreach(o =>
      this.sampleDistributions.foreach(_ += o))
    other.sampleCompare.foreach(o => this.sampleCompare.foreach(_ += o))
    other.infoFields.foreach(o => this.infoFields(o._1) += o._2)
    other.genotypeFields.foreach(o => this.genotypeFields(o._1) += o._2)
    this
  }

  /** Writing output files to a given directory */
  def writeStats(outputDir: File): Unit = {
    general.foreach(_.writeToTsv(new File(outputDir, "general.tsv")))
    genotype.foreach(_.writeToTsv(new File(outputDir, "genotype.tsv")))
    sampleDistributions.foreach { x =>
      val dir = new File(outputDir, "sample_distributions")
      dir.mkdir()
      x.writeToDir(dir)
    }
    sampleCompare.foreach { x =>
      val dir = new File(outputDir, "sample_compare")
      dir.mkdir()
      x.writeAllFiles(dir)
    }

    infoFields.foreach(x =>
      x._2.writeHistogram(new File(outputDir, s"info.${x._1}.tsv")))
    genotypeFields.foreach(x =>
      x._2.writeToFile(new File(outputDir, s"genotype.${x._1}.tsv")))
  }
}

object StatsTotal {

  /**
    * Creates a empty [[StatsTotal]] class instance
    * @param header Vcf header
    * @param cmdArgs Command line args from vcfstats
    * @return
    */
  def empty(header: VCFHeader, cmdArgs: Args): StatsTotal = {
    if (cmdArgs.skipGeneral) None else Some(new vcf.GeneralStats)
    StatsTotal(
      if (cmdArgs.skipGeneral) None else Some(new vcf.GeneralStats),
      if (cmdArgs.skipGenotype) None else Some(new vcf.GenotypeStats(header)),
      if (cmdArgs.skipSampleDistributions) None
      else Some(new vcf.SampleDistributions),
      if (cmdArgs.skipSampleCompare) None else Some(new SampleCompare(header)),
      cmdArgs.infoTags.map(x => x -> x.newInfoCount(header)).toMap,
      cmdArgs.genotypeTags.map(x => x -> x.newGenotypeCount(header)).toMap
    )
  }
}
