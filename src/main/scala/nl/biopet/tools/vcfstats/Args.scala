/*
 * Copyright (c) 2017 Sequencing Analysis Support Core - Leiden University Medical Center
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
