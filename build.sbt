organization := "com.github.biopet"
organizationName := "Biopet"

startYear := Some(2014)

name := "VcfStats"
biopetUrlName := "vcfstats"

biopetIsTool := true

fork in Test := true

mainClass in assembly := Some("nl.biopet.tools.vcfstats.VcfStats")

developers += Developer(id = "ffinfo",
                        name = "Peter van 't Hof",
                        email = "pjrvanthof@gmail.com",
                        url = url("https://github.com/ffinfo"))
developers += Developer(id = "rhpvorderman",
                        name = "Ruben Vorderman",
                        email = "r.h.p.vorderman@lumc.nl",
                        url = url("https://github.com/rhpvorderman"))

scalaVersion := "2.11.12"

dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-core" % "2.8.7"
dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.8.7"
dependencyOverrides += "com.fasterxml.jackson.module" % "jackson-module-scala_2.11" % "2.8.7"

libraryDependencies += "com.github.biopet" %% "tool-utils" % "0.4"
libraryDependencies += "com.github.biopet" %% "spark-utils" % "0.4-SNAPSHOT" changing ()

libraryDependencies += "org.apache.spark" %% "spark-core" % "2.3.0" % Provided
libraryDependencies += "org.apache.spark" %% "spark-sql" % "2.3.0" % Provided

libraryDependencies += "com.github.biopet" %% "tool-test-utils" % "0.2.2" % Test
