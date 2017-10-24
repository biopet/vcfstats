package nl.biopet.tools.vcfstats

import java.io.File

import htsjdk.variant.variantcontext.VariantContext
import htsjdk.variant.vcf.VCFHeader
import nl.biopet.utils.ngs.vcf

case class StatsTotal(
    general: Option[vcf.GeneralStats],
    genotype: Option[vcf.GenotypeStats]) { //TODO: add more Collectors
  def addRecord(record: VariantContext, cmdArgs: Args): Unit = {
    general.foreach(_.addRecord(record))
    genotype.foreach(_.addRecord(record))
  }

  def +=(other: StatsTotal): StatsTotal = {
    require(other.general.isDefined == this.general.isDefined)
    require(other.genotype.isDefined == this.genotype.isDefined)
    other.general.foreach(o => this.general.foreach(_ += o))
    other.genotype.foreach(o => this.genotype.foreach(_ += o))
    this
  }

  def writeStats(outputDir: File): Unit = {
    general.foreach(_.writeToTsv(new File(outputDir, "general.tsv")))
    genotype.foreach(_.writeToTsv(new File(outputDir, "genotype.tsv")))
  }
}

object StatsTotal {
  def empty(header: VCFHeader, cmdArgs: Args): StatsTotal = {
    StatsTotal(Some(new vcf.GeneralStats), Some(new vcf.GenotypeStats(header)))
  }
}
