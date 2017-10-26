package nl.biopet.tools.vcfstats

import java.io.File

import htsjdk.variant.variantcontext.VariantContext
import htsjdk.variant.vcf.VCFHeader
import nl.biopet.utils.ngs.vcf
import nl.biopet.utils.ngs.vcf.SampleCompare

case class StatsTotal(
    general: Option[vcf.GeneralStats],
    genotype: Option[vcf.GenotypeStats],
    sampleCompare: Option[SampleCompare]) { //TODO: add more Collectors
  def addRecord(record: VariantContext, cmdArgs: Args): Unit = {
    general.foreach(_.addRecord(record))
    genotype.foreach(_.addRecord(record))
    sampleCompare.foreach(_.addRecord(record, cmdArgs.sampleToSampleMinDepth))
  }

  def +=(other: StatsTotal): StatsTotal = {
    require(other.general.isDefined == this.general.isDefined)
    require(other.genotype.isDefined == this.genotype.isDefined)
    require(other.sampleCompare.isDefined == this.sampleCompare.isDefined)
    other.general.foreach(o => this.general.foreach(_ += o))
    other.genotype.foreach(o => this.genotype.foreach(_ += o))
    other.sampleCompare.foreach(o => this.sampleCompare.foreach(_ += o))
    this
  }

  def writeStats(outputDir: File): Unit = {
    general.foreach(_.writeToTsv(new File(outputDir, "general.tsv")))
    genotype.foreach(_.writeToTsv(new File(outputDir, "genotype.tsv")))
    sampleCompare.foreach { x =>
      val dir = new File(outputDir, "sample_compare")
      dir.mkdir()
      x.writeAllFiles(dir)
    }
  }
}

object StatsTotal {
  def empty(header: VCFHeader, cmdArgs: Args): StatsTotal = {
    if (cmdArgs.skipGeneral) None else Some(new vcf.GeneralStats)
    StatsTotal(
      if (cmdArgs.skipGeneral) None else Some(new vcf.GeneralStats),
      if (cmdArgs.skipGenotype) None else Some(new vcf.GenotypeStats(header)),
      if (cmdArgs.skipSampleCompare) None else Some(new SampleCompare(header))
    )
  }
}
