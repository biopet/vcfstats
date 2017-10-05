package nl.biopet.tools.vcfstats

import java.io.File

case class Args(inputFile: File = null,
                outputDir: File = null,
                referenceFile: File = null,
                intervals: Option[File] = None,
                infoTags: List[String] = Nil,
                genotypeTags: List[String] = Nil,
                allInfoTags: Boolean = false,
                allGenotypeTags: Boolean = false,
                binSize: Int = 10000000,
                maxContigsInSingleJob: Int = 250,
                writeBinStats: Boolean = false,
                localThreads: Int = 1,
                notWriteContigStats: Boolean = false,
                sparkMaster: Option[String] = None,
                sparkConfigValues: Map[String, String] = Map(
                          "spark.memory.fraction" -> "0.1",
                          "spark.memory.storageFraction" -> "0.2"
                        ),
                contigSampleOverlapPlots: Boolean = false)
