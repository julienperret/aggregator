package fr.ign.aggregator

import java.util.Calendar

import better.files.File
import com.vividsolutions.jts.geom.Geometry
import org.geotools.data.{DataUtilities, FeatureWriter, Transaction}
import org.geotools.data.shapefile.{ShapefileDataStore, ShapefileDataStoreFactory}
import org.opengis.feature.simple.{SimpleFeature, SimpleFeatureType}

object BuildingAggregator extends App {
  // DOES NOT WORK FOR THE ENTIRE COUNTRY: Shapefile Too large!!!
  val inputDirectory = "BDTOPO/BATI_BDTOPO_2017"
  val outputFileName = "BATIMENT_2017.shp"
  val sameSchema = false
  println(Calendar.getInstance.getTime + " let's go")
  def readFile(aFile: File, writer:FeatureWriter[SimpleFeatureType, SimpleFeature], schema: SimpleFeatureType) = {
    val store = new ShapefileDataStore(aFile.toJava.toURI.toURL)
    if (!store.getSchema.equals(schema)) println("schema = " + store.getSchema)
    var i = 0
    try {
      val reader = store.getFeatureReader
      try {
        while (reader.hasNext) {
          val feature = reader.next
          val simpleFeature = writer.next
          if (sameSchema) {
            simpleFeature.setAttributes(feature.getAttributes)
          } else {
            simpleFeature.setAttributes(Array[AnyRef](feature.getDefaultGeometry.asInstanceOf[Geometry].getCentroid))
          }
          writer.write()
          i+=1
        }
      } finally reader.close()
    } finally store.dispose()
    println(aFile + ": " + i + " features")
  }
  val children = File(inputDirectory).collectChildren(f=>f.name.contains("BATI") && f.name.endsWith("SHP"))
  val outFile = File(outputFileName)
  val getSchema = {
    if (sameSchema) {
      val first = File(inputDirectory).collectChildren(f => f.name.contains("BATI") && f.name.endsWith("SHP")).next
      val firstStore = new ShapefileDataStore(first.toJava.toURI.toURL)
      val schema = firstStore.getSchema
      println(schema)
      firstStore.dispose()
      schema
    } else {
      val factory = new ShapefileDataStoreFactory
      val dataStore = factory.createDataStore(outFile.toJava.toURI.toURL)
      val featureTypeName = "Object"
      val specs = "geom:Point:srid=2154"
      val featureType = DataUtilities.createType(featureTypeName, specs)
      dataStore.createSchema(featureType)
      val schema = dataStore.getSchema
      println(schema)
      dataStore.dispose()
      schema
    }
  }
  def schema = getSchema
  val factory = new ShapefileDataStoreFactory
  val dataStore = factory.createDataStore(outFile.toJava.toURI.toURL)
  dataStore.createSchema(schema)
  val typeName = dataStore.getTypeNames()(0)
  val writer = dataStore.getFeatureWriterAppend(typeName, Transaction.AUTO_COMMIT)
  System.setProperty("org.geotools.referencing.forceXY","true")
  for (elem <- children) readFile(elem, writer, schema)
  println(Calendar.getInstance.getTime + " almost done")
  writer.close()
  dataStore.dispose()
  println(Calendar.getInstance.getTime + " done")
}
