package fr.ign.aggregator

import com.vividsolutions.jts.geom.{Geometry, GeometryFactory, Point, Polygon}
import com.vividsolutions.jts.operation.polygonize.Polygonizer
import com.vividsolutions.jts.geom.util.LinearComponentExtracter
import java.util
import java.util.Calendar

import better.files.File
import com.vividsolutions.jts.geom.GeometryFactory.toLineStringArray
import org.geotools.data.{DataUtilities, Transaction}
import org.geotools.data.shapefile.{ShapefileDataStore, ShapefileDataStoreFactory}
import org.opengis.feature.simple.SimpleFeature

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

object Polygonize extends App {
  val fact = new GeometryFactory()
  def getLines(inputFeatures: Seq[Geometry]) = {
    val linesList = new util.ArrayList[Geometry]
    val lineFilter = new LinearComponentExtracter(linesList)
    for (feature <- inputFeatures) feature.apply(lineFilter)
    linesList
  }
  def nodeLines(lines: util.Collection[Geometry]) = {
    val linesGeom = fact.createMultiLineString(toLineStringArray(lines))
    var unionInput: Geometry = fact.createMultiLineString(null)
    val point = extractPoint(lines)
    if (point != null) unionInput = point
    val noded = linesGeom.union(unionInput)
    val nodedList = new util.ArrayList[Geometry]
    nodedList.add(noded)
    nodedList
  }.asScala
  def extractPoint(lines: util.Collection[Geometry]) = {
    var point:Point = null
    // extract first point from first non-empty geometry
    for (geometry <- lines.asScala) {
      if (!geometry.isEmpty) {
        val p = geometry.getCoordinate
        point = geometry.getFactory.createPoint(p)
      }
    }
    point
  }
  def getFeatures(aFile: File, filter: SimpleFeature=>Boolean = _=>true) = {
    val store = new ShapefileDataStore(aFile.toJava.toURI.toURL)
    val array = ArrayBuffer[Geometry]()
    try {
      val reader = store.getFeatureReader
      try {
        Try {
          val featureReader = Iterator.continually(reader.next).takeWhile(_ => reader.hasNext)
          featureReader.foreach { feature =>
            if (filter(feature)) array += feature.getDefaultGeometry.asInstanceOf[Geometry]
          }
        }
      } finally reader.close()
    } finally store.dispose()
    array
  }

  def addFeatures(p: Polygonizer, inputFeatures: Seq[Geometry]) = {
    println(Calendar.getInstance.getTime + " node lines")
    val lines = getLines(inputFeatures)
    val nodedLines = nodeLines(lines)
    val size = nodedLines.size
    println(Calendar.getInstance.getTime + s" insert lines ($size)")
    var i:Int = 0
    for (geometry <- nodedLines) {
      p.add(geometry)
      i = i+1
      if (i%100 == 0) println(Calendar.getInstance.getTime + s" $i / $size")
    }
  }
  val polygonizer = new Polygonizer()
  val folderData = "/home/julien/data/bdtopo2017"
  val folder = "/home/julien/devel/aggregator"
  def filterPosSol(simpleFeature: SimpleFeature) = simpleFeature.getAttribute("POS_SOL").asInstanceOf[Int] >= 0
  val childrenRoad = File(folderData).collectChildren(f =>
    (f.pathAsString.contains("D075") || f.pathAsString.contains("D077") || f.pathAsString.contains("D078") ||
      f.pathAsString.contains("D091") || f.pathAsString.contains("D092") || f.pathAsString.contains("D093") ||
      f.pathAsString.contains("D094") || f.pathAsString.contains("D095"))
      && f.name.equalsIgnoreCase("ROUTE.SHP"))
  val childrenRivers = File(folderData).collectChildren(f =>
    (f.pathAsString.contains("D075") || f.pathAsString.contains("D077") || f.pathAsString.contains("D078") ||
      f.pathAsString.contains("D091") || f.pathAsString.contains("D092") || f.pathAsString.contains("D093") ||
      f.pathAsString.contains("D094") || f.pathAsString.contains("D095"))
      && f.name.equalsIgnoreCase("TRONCON_COURS_EAU.SHP"))
  val childrenRails = File(folderData).collectChildren(f =>
    (f.pathAsString.contains("D075") || f.pathAsString.contains("D077") || f.pathAsString.contains("D078") ||
      f.pathAsString.contains("D091") || f.pathAsString.contains("D092") || f.pathAsString.contains("D093") ||
      f.pathAsString.contains("D094") || f.pathAsString.contains("D095"))
      && f.name.equalsIgnoreCase("TRONCON_VOIE_FERREE.SHP"))
  val files = childrenRoad.toSeq ++ childrenRivers.toSeq ++ childrenRails.toSeq
  val features = files.flatMap(file => {
    println(Calendar.getInstance.getTime + s" handling " + file)
    getFeatures(file, filterPosSol)
  })
  println(Calendar.getInstance.getTime + s" adding features")
  addFeatures(polygonizer, features)

  val specs = "geom:Polygon:srid=2154"
  val out = File(folder) / "polygons.shp"
  val geometryFactory = new GeometryFactory
  val factory = new ShapefileDataStoreFactory
  val dataStore = factory.createDataStore(out.toJava.toURI.toURL)
  val featureTypeName = "Object"
  val featureType = DataUtilities.createType(featureTypeName, specs)
  dataStore.createSchema(featureType)
  val typeName = dataStore.getTypeNames()(0)
  val writer = dataStore.getFeatureWriterAppend(typeName, Transaction.AUTO_COMMIT)
  System.setProperty("org.geotools.referencing.forceXY","true")
  println(Calendar.getInstance.getTime + " now with the real stuff")
  polygonizer.getPolygons.forEach{ p=>
    val feature = writer.next()
    feature.setAttributes(Array[AnyRef](p.asInstanceOf[Polygon]))
    writer.write()
  }
  println(Calendar.getInstance.getTime + " done")
  writer.close()
  dataStore.dispose()

}
