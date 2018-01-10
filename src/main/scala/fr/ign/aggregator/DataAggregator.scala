package fr.ign.aggregator

import better.files.File
import com.vividsolutions.jts.geom.{GeometryFactory, MultiPolygon}
import org.geotools.data.{DataUtilities, FeatureWriter, Transaction}
import org.geotools.data.shapefile.{ShapefileDataStore, ShapefileDataStoreFactory}
import org.geotools.geometry.jts.JTS
import org.geotools.referencing.CRS
import org.opengis.feature.simple.{SimpleFeature, SimpleFeatureType}
import org.opengis.referencing.operation.MathTransform

import scala.util.Try

/**
  * Created by julien on 28/08/17.
  */
object DataAggregator extends App {
  def readFile(aFile: File, writer:FeatureWriter[SimpleFeatureType, SimpleFeature], transform: MathTransform) = {
    println(aFile)
    val store = new ShapefileDataStore(aFile.toJava.toURI.toURL)
    var i = 0
    try {
      val reader = store.getFeatureReader
      try {
        Try {
          val featureReader = Iterator.continually(reader.next).takeWhile(_ => reader.hasNext)
          featureReader.foreach { feature =>
            val default = feature.getDefaultGeometry.asInstanceOf[MultiPolygon]
            val geom = JTS.transform(default, transform).asInstanceOf[MultiPolygon]
            val height = feature.getAttribute("HAUTEUR").asInstanceOf[Int]
            val values = Array[AnyRef](
              geom,
              height.asInstanceOf[AnyRef]
            )
            val simpleFeature = writer.next
            simpleFeature.setAttributes(values)
            writer.write
            i+=1
          }
        }
      } finally reader.close
    } finally store.dispose
    println("added " + i + " features")
  }
  val children = File("/home/julien/data/dpsg/dpsg2017-06-00426/BDTOPO/1_DONNEES_LIVRAISON_2017-06-00426").
    collectChildren(f=>
      (f.pathAsString.contains("D075")||f.pathAsString.contains("D077")||f.pathAsString.contains("D078")||
        f.pathAsString.contains("D091")||f.pathAsString.contains("D092")||f.pathAsString.contains("D093")||
        f.pathAsString.contains("D094")||f.pathAsString.contains("D095")||
        f.pathAsString.contains("D027")||f.pathAsString.contains("D060")||
        f.pathAsString.contains("D002")||f.pathAsString.contains("D051")||
        f.pathAsString.contains("D010")||f.pathAsString.contains("D089")||
        f.pathAsString.contains("D045")||f.pathAsString.contains("D028"))
        && f.name.contains("BATI_INDIFFERENCIE") && f.name.endsWith("SHP"))
  val outFile = File("buildings_laea_greater.shp")
  val specs = "geom:MultiPolygon:srid=3035,HAUTEUR:Integer"
  val geometryFactory = new GeometryFactory
  val factory = new ShapefileDataStoreFactory
  val dataStore = factory.createDataStore(outFile.toJava.toURI.toURL)
  val featureTypeName = "Object"
  val featureType = DataUtilities.createType(featureTypeName, specs)
  dataStore.createSchema(featureType)
  val typeName = dataStore.getTypeNames()(0)
  val writer = dataStore.getFeatureWriterAppend(typeName, Transaction.AUTO_COMMIT)
  val inCRS = CRS.decode("EPSG:2154")
  val outCRS = CRS.decode("EPSG:3035")
  System.setProperty("org.geotools.referencing.forceXY","true")
  val transform = CRS.findMathTransform(inCRS, outCRS, true)
  for (elem <- children) readFile(elem, writer, transform)
  println("done")
  writer.close
  dataStore.dispose
  println("done")
}
