package nl.biopet.tools.vcfstats

import java.io.File

import nl.biopet.utils.tool.AbstractOptParser

class ArgsParser(cmdName: String) extends AbstractOptParser[Args](cmdName) {
  opt[File]('I', "inputFile") required () unbounded () maxOccurs 1 valueName "<file>" action {
    (x, c) =>
      c.copy(inputFile = x.getAbsoluteFile)
  } validate { x =>
    if (x.exists) success else failure("Input VCF required")
  } text "Input VCF file (required)"
  opt[File]('R', "referenceFile") required () unbounded () maxOccurs 1 valueName "<file>" action {
    (x, c) =>
      c.copy(referenceFile = x)
  } validate { x =>
    if (x.exists) success else failure("Reference file required")
  } text "Fasta reference which was used to call input VCF (required)"
  opt[File]('o', "outputDir") required () unbounded () maxOccurs 1 valueName "<file>" action {
    (x, c) =>
      c.copy(outputDir = x.getAbsoluteFile)
  } text "Path to directory for output (required)"
  opt[File]('i', "intervals") unbounded () valueName "<file>" action {
    (x, c) =>
      c.copy(intervals = Some(x))
  } text "Path to interval (BED) file (optional)"
  opt[String]("infoTag") unbounded () valueName "<tag>" action { (x, c) =>
    c.copy(infoTags = x :: c.infoTags)
  } text s"Summarize these info tags"
  opt[String]("genotypeTag") unbounded () valueName "<tag>" action { (x, c) =>
    c.copy(genotypeTags = x :: c.genotypeTags)
  } text s"Summarize these genotype tags"
  opt[Unit]("allInfoTags") unbounded () action { (_, c) =>
    c.copy(allInfoTags = true)
  } text "Summarize all info tags. Default false"
  opt[Unit]("allGenotypeTags") unbounded () action { (_, c) =>
    c.copy(allGenotypeTags = true)
  } text "Summarize all genotype tags. Default false"
  opt[Int]("sampleToSampleMinDepth") unbounded () action { (x, c) =>
    c.copy(sampleToSampleMinDepth = Some(x))
  } text "Minimal depth require to consider sample to sample comparison"
  opt[Int]("binSize") unbounded () action { (x, c) =>
    c.copy(binSize = x)
  } text "Binsize in estimated base pairs"
  opt[Int]("maxContigsInSingleJob") unbounded () action { (x, c) =>
    c.copy(maxContigsInSingleJob = x)
  } text s"Max number of bins to be combined, default is 250"
  opt[Unit]("writeBinStats") unbounded () action { (_, c) =>
    c.copy(writeBinStats = true)
  } text "Write bin statistics. Default False"
  opt[Int]('t', "localThreads") unbounded () action { (x, c) =>
    c.copy(localThreads = x)
  } text s"Number of local threads to use"
  opt[Unit]("notWriteContigStats") unbounded () action { (_, c) =>
    c.copy(notWriteContigStats = true)
  } text s"Number of local threads to use"
  opt[Unit]("skipGeneral") unbounded () action { (_, c) =>
    c.copy(skipGeneral = true)
  } text s"Skipping general stats"
  opt[Unit]("skipGenotype") unbounded () action { (_, c) =>
    c.copy(skipGenotype = true)
  } text s"Skipping genotype stats"
  opt[Unit]("skipSampleCompare") unbounded () action { (_, c) =>
    c.copy(skipSampleCompare = true)
  } text s"Skipping sample compare"
  opt[String]("sparkMaster") unbounded () action { (x, c) =>
    c.copy(sparkMaster = Some(x))
  } text s"Spark master to use"
  opt[String]("sparkExecutorMemory") unbounded () action { (x, c) =>
    c.copy(
      sparkConfigValues = c.sparkConfigValues + ("spark.executor.memory" -> x))
  } text s"Spark executor memory to use"
  opt[(String, String)]("sparkConfigValue") unbounded () action { (x, c) =>
    c.copy(sparkConfigValues = c.sparkConfigValues + (x._1 -> x._2))
  } text s"Add values to the spark config"
}
