organization := "fr.ign"

name := "aggregator"

version := "1.0-SNAPSHOT"

scalaVersion := "2.12.6"

maintainer := "julien.perret@gmail.com"

val geotoolsVersion = "18.4"

val jtsVersion = "1.14.0"

val breezeVersion = "0.13.1"

resolvers ++= Seq(
  "osgeo" at "http://download.osgeo.org/webdav/geotools/",
  "geosolutions" at "http://maven.geo-solutions.it/",
  "geotoolkit" at "http://maven.geotoolkit.org/",
//  "IGN Snapshots" at "https://forge-cogit.ign.fr/nexus/content/repositories/snapshots/",
  "Local Maven" at Path.userHome.asFile.toURI.toURL + ".m2/repository"
)

libraryDependencies ++= Seq (
  "org.geotools" % "gt-referencing" % geotoolsVersion,
  "org.geotools" % "gt-shapefile" % geotoolsVersion,
  "org.geotools" % "gt-epsg-wkt" % geotoolsVersion,
  "org.geotools" % "gt-cql" % geotoolsVersion,
  "org.geotools" % "gt-geotiff" % geotoolsVersion,
  "org.geotools" % "gt-image" % geotoolsVersion,
  "org.geotools" % "gt-coverage" % geotoolsVersion,
  "org.geotools" % "gt-geojson" % geotoolsVersion,
  "com.vividsolutions" % "jts-core" % jtsVersion,
  "com.github.pathikrit" %% "better-files" % "2.17.1",
  "org.apache.commons" % "commons-compress" % "1.18",
  "org.tukaani" % "xz" % "1.8"
//  "fr.ign.cogit" % "evidence4j" % "1.1-SNAPSHOT" from "https://forge-cogit.ign.fr/nexus/content/repositories/snapshots/fr/ign/cogit/evidence4j/1.1-SNAPSHOT/evidence4j-1.1-20160229.093026-1.jar"
)

libraryDependencies += "com.github.haifengl" %% "smile-scala" % "1.5.1"

libraryDependencies += "org.jgrapht" % "jgrapht-core" % "1.1.0"

val monocleVersion = "1.5.0" // 1.5.0-cats based on cats 1.0.x

libraryDependencies ++= Seq(
  "com.github.julien-truffaut" %%  "monocle-core"  % monocleVersion,
  "com.github.julien-truffaut" %%  "monocle-macro" % monocleVersion
)


addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

enablePlugins(SbtOsgi)

osgiSettings

OsgiKeys.exportPackage := Seq("fr.ign.aggregator.*")

OsgiKeys.importPackage := Seq("*;resolution:=optional")

OsgiKeys.privatePackage := Seq("""
                                 |!scala.*,!java.*,META-INF.*;-split-package:=merge-first,
                                 |*;-split-package:=merge-first
                                 |""".stripMargin)

//OsgiKeys.embeddedJars := (Keys.externalDependencyClasspath in Compile).value map (_.data) filter (_.name.startsWith("gt-"))

OsgiKeys.requireCapability := ""

enablePlugins(JavaAppPackaging)

mainClass in Compile := Some("fr.ign.aggregator.Workflow")
