organization := "nl.biopet"
name := "tool-template"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.11.11"

resolvers += Resolver.mavenLocal

lazy val toolTemplate = project in file(".")

libraryDependencies += "nl.biopet" %% "biopet-tool-utils" % "0.1.0-SNAPSHOT"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.1" % Test
libraryDependencies += "org.testng" % "testng" % "6.8" % Test
