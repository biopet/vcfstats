# Manual

VcfStats requires at least a VCF file, the original reference file that was used to call the VCF, and an output directory.

```bash
java -jar VcfStats-version \
-I input.vcf \
-R reference.fa \
-o my_stats/
```

VcfStats has several flags that can be specified optionally:
* `-i` or `--intervals` to specifiy a BED file with intervals.
* `--infoTag` to specify which info tag will be summarized can be specified multiple times for multiple tags.
* `--genotypeTag` to specify which genotype tag will be summarized can be specified multiple times for multiple tags.
* `--allInfoTags` to summarize all info tags.
* `--allGenotypeTags` to summarize all genotype tags.
* `--sampleToSampleMinDepth` minimal depth require to consider sample to sample comparison.
* `--binSize` the bin size in estimated base paris.
* `--writeBinStats` also output the bin stats.
* `--notWriteContigStats` do not output contig stats.

There are also several flags to allocate computational resources to the VcfStats job:
* `--maxContigsInSingleJob` the maximum amount of contigs that will be run in a single job. If the number of contigs is higher than this number the run will be split across multiple jobs.
* `-t` or `--localThreads` the number of local threads t use.
* `--sparkMaster` the SPARK master to use.
* `--sparkExecutorMemory` the amount of memory assigned to each SPARK executor.
* `--sparkConfigValue` can be specified multiple times, add extra values to the spark config.