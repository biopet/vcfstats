package nl.biopet.tools.vcfstats

import htsjdk.variant.vcf.VCFHeader
import nl.biopet.utils.ngs.vcf.{FieldMethod, GenotypeFieldCounts, InfoFieldCounts}

case class VcfField(key: String, method: FieldMethod.Value) {
  def newInfoCount(header: VCFHeader): InfoFieldCounts = {
    new InfoFieldCounts(header.getInfoHeaderLine(key), method)
  }

  def newGenotypeCount(header: VCFHeader): GenotypeFieldCounts = {
    new GenotypeFieldCounts(header, header.getFormatHeaderLine(key), method)
  }

  override def toString = s"${key}_$method"
}

object VcfField {
  def fromArg(arg: String): VcfField = {
    val values = arg.split(":")
    require(values.size == 2, s"A field should be formated like: <tag>:<method>. Possible methods: ${FieldMethod.values}")
    val key: String = values.head
    val method: FieldMethod.Value = FieldMethod.withName(values(1))
    VcfField(key, method)
  }
}