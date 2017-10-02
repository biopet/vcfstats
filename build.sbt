organization := "com.github.biopet"
//TODO: change name
name := "tool-template"

scalaVersion := "2.11.11"

resolvers += Resolver.mavenLocal

//TODO: change name
lazy val toolTemplate = project in file(".")

libraryDependencies += "com.github.biopet" %% "biopet-tool-utils" % "0.1.0-SNAPSHOT"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.1" % Test
libraryDependencies += "org.testng" % "testng" % "6.8" % Test

//TODO: change mainClass
mainClass in assembly := Some("nl.biopet.tools.template.Main")

useGpg := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

import ReleaseTransformations._
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  //releaseStepCommand("publishSigned"),
  setNextVersion,
  commitNextVersion//,
  //releaseStepCommand("sonatypeReleaseAll"),
  //pushChanges
)
