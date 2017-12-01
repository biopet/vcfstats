package nl.biopet.tools.vcfstats

import java.io.File

import nl.biopet.utils.tool.AbstractOptParser

import nl.biopet.utils.ngs.vcf.VcfField

class ArgsParser(cmdName: String) extends AbstractOptParser[Args](cmdName) {
  opt[File]('I', "inputFile") required () valueName "<file>" action { (x, c) =>
    c.copy(inputFile = x.getAbsoluteFile)
  } text "Input VCF file (required)"
  opt[File]('R', "referenceFile") required () valueName "<file>" action {
    (x, c) =>
      c.copy(referenceFile = x)
  } text "Fasta reference which was used to call input VCF (required)"
  opt[File]('o', "outputDir") required () valueName "<file>" action { (x, c) =>
    c.copy(outputDir = x.getAbsoluteFile)
  } text "Path to directory for output (required)"
  opt[File]("intervals") valueName "<file>" action { (x, c) =>
    c.copy(intervals = Some(x))
  } text "Path to interval (BED) file (optional)"
  opt[String]("infoTag") unbounded () valueName "<tag>" action { (x, c) =>
    c.copy(infoTags = VcfField.fromArg(x) :: c.infoTags)
  } text s"Summarize these info tags"
  opt[String]("genotypeTag") unbounded () valueName "<tag>" action { (x, c) =>
    c.copy(genotypeTags = VcfField.fromArg(x) :: c.genotypeTags)
  } text s"Summarize these genotype tags"
  opt[Int]("sampleToSampleMinDepth") action { (x, c) =>
    c.copy(sampleToSampleMinDepth = Some(x))
  } text "Minimal depth require to consider sample to sample comparison"
  opt[Int]("binSize") action { (x, c) =>
    c.copy(binSize = x)
  } text "Binsize in estimated base pairs"
  opt[Int]("maxContigsInSingleJob") action { (x, c) =>
    c.copy(maxContigsInSingleJob = x)
  } text s"Max number of bins to be combined, default is 250"
  opt[Unit]("writeBinStats") action { (_, c) =>
    c.copy(writeBinStats = true)
  } text "Write bin statistics. Default False"
  opt[Int]('t', "localThreads") action { (x, c) =>
    c.copy(localThreads = x)
  } text s"Number of local threads to use"
  opt[Unit]("notWriteContigStats") action { (_, c) =>
    c.copy(notWriteContigStats = true)
  } text s"Number of local threads to use"
  opt[Unit]("skipGeneral") action { (_, c) =>
    c.copy(skipGeneral = true)
  } text s"Skipping general stats"
  opt[Unit]("skipGenotype") action { (_, c) =>
    c.copy(skipGenotype = true)
  } text s"Skipping genotype stats"
  opt[Unit]("skipSampleDistributions") action { (_, c) =>
    c.copy(skipSampleDistributions = true)
  } text s"Skipping sample distributions stats"
  opt[Unit]("skipSampleCompare") action { (_, c) =>
    c.copy(skipSampleCompare = true)
  } text s"Skipping sample compare"
  opt[String]("sparkMaster") action { (x, c) =>
    c.copy(sparkMaster = Some(x))
  } text s"Spark master to use"
  opt[String]("sparkExecutorMemory") action { (x, c) =>
    c.copy(
      sparkConfigValues = c.sparkConfigValues + ("spark.executor.memory" -> x))
  } text s"Spark executor memory to use"
  opt[(String, String)]("sparkConfigValue") unbounded () action { (x, c) =>
    c.copy(sparkConfigValues = c.sparkConfigValues + (x._1 -> x._2))
  } text s"Add values to the spark config"
}