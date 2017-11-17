package nl.biopet.tools.vcfstats

import java.io.File

import nl.biopet.utils.ngs.vcf.VcfField

case class Args(inputFile: File = null,
                outputDir: File = null,
                referenceFile: File = null,
                intervals: Option[File] = None,
                infoTags: List[VcfField] = Nil,
                genotypeTags: List[VcfField] = Nil,
                binSize: Int = 10000000,
                maxContigsInSingleJob: Int = 250,
                writeBinStats: Boolean = false,
                localThreads: Int = 1,
                notWriteContigStats: Boolean = false,
                sparkMaster: Option[String] = None,
                sparkConfigValues: Map[String, String] = Map(
                  "spark.rpc.message.maxSize" -> "500"
                ),
                contigSampleOverlapPlots: Boolean = false,
                sampleToSampleMinDepth: Option[Int] = None,
                skipGeneral: Boolean = false,
                skipGenotype: Boolean = false,
                skipSampleDistributions: Boolean = false,
                skipSampleCompare: Boolean = false)
