package fr.ign.aggregator

import java.util.Calendar

import better.files.File
import com.vividsolutions.jts.geom.{Geometry, MultiPolygon, PrecisionModel}
import com.vividsolutions.jts.index.strtree.STRtree
import com.vividsolutions.jts.precision.GeometryPrecisionReducer
import org.geotools.data.{DataUtilities, Transaction}
import org.geotools.data.shapefile.{ShapefileDataStore, ShapefileDataStoreFactory}

import scala.util.Try
import fr.ign.aggregator.Utils.toPolygon

object Package extends App {
  def index(aFile: File) = {
    val store = new ShapefileDataStore(aFile.toJava.toURI.toURL)
    val index = new STRtree()
    var i: Int = 0
    try {
      val reader = store.getFeatureReader
      try {
        Try {
          val featureReader = Iterator.continually(reader.next).takeWhile(_ => reader.hasNext)
          featureReader.foreach { feature =>
            val geom = toPolygon(feature.getDefaultGeometry)
            index.insert(geom.getEnvelopeInternal, (geom,feature.getAttribute("IDPAR").toString))
            i += 1
            if (i % 10000 == 0) println(i)
          }
        }
      } finally reader.close()
    } finally store.dispose()
    index
  }

  def writeBuildingBlock(i: Int, dir: File, parcels: Seq[(Geometry, String)]) = {
    val specs = "geom:MultiPolygon:srid=2154,IDPAR:String"
    val factory = new ShapefileDataStoreFactory
    val file = dir / s"block_$i" / "parcels.shp"
    println("creating file " + file)
    file.parent.createDirectories()
    val dataStore = factory.createDataStore(file.toJava.toURI.toURL)
    val featureTypeName = "Object"
    val featureType = DataUtilities.createType(featureTypeName, specs)
    dataStore.createSchema(featureType)
    val typeName = dataStore.getTypeNames()(0)
    val writer = dataStore.getFeatureWriterAppend(typeName, Transaction.AUTO_COMMIT)
    System.setProperty("org.geotools.referencing.forceXY","true")
    println(Calendar.getInstance.getTime + " now with the real stuff")
    parcels.foreach{p=>
      val f = writer.next()
      f.setAttributes(Array[AnyRef](p._1, p._2))
      writer.write()
    }
    println(Calendar.getInstance.getTime + " done")
    writer.close()
    dataStore.dispose()
  }
  val folder = "/home/julien/devel/aggregator"
  val out = File("/home/julien/devel/aggregator") / "output"
  val polygons = File(folder) / "polygons.shp"
  val parcels = File(folder) / "parcels_idf.shp"
  println(Calendar.getInstance.getTime + " building index")
  val parcelIndex = index(parcels)
  println(Calendar.getInstance.getTime + " index done")

  val writeSeparate = false

  val (dataStore, writer) = {
    val specs = "geom:MultiPolygon:srid=2154,IDPAR:String,idBlock:Integer"
    val factory = new ShapefileDataStoreFactory
    val file = out / "parcels.shp"
    println("creating file " + file)
    file.parent.createDirectories()
    val dataStore = factory.createDataStore(file.toJava.toURI.toURL)
    val featureTypeName = "Object"
    val featureType = DataUtilities.createType(featureTypeName, specs)
    dataStore.createSchema(featureType)
    val typeName = dataStore.getTypeNames()(0)
    val writer = dataStore.getFeatureWriterAppend(typeName, Transaction.AUTO_COMMIT)
    System.setProperty("org.geotools.referencing.forceXY","true")
    println(Calendar.getInstance.getTime + " now with the real stuff")
    (dataStore, writer)
  }
  val gpr = new GeometryPrecisionReducer(new PrecisionModel(1000))
  val store = new ShapefileDataStore(polygons.toJava.toURI.toURL)
  var i: Int = 0
  try {
    val reader = store.getFeatureReader
    try {
      Try {
        val featureReader = Iterator.continually(reader.next).takeWhile(_ => reader.hasNext)
        featureReader.foreach { feature =>
          val geom = gpr.reduce(feature.getDefaultGeometry.asInstanceOf[Geometry])
          val intersects = parcelIndex.query(geom.getEnvelopeInternal).toArray.toSeq.map(_.asInstanceOf[(MultiPolygon, String)]).map(x=>(gpr.reduce(x._1),x._2)).filter(_._1.intersects(geom))
          //println("intersects " + intersects.size)
          val selected = intersects.filter(p=>p._1.intersection(geom).getArea > 0.9 * p._1.getArea)
          //println("selected " + selected.size)
          if (selected.nonEmpty) {
            if (writeSeparate) writeBuildingBlock(i, out, selected)
            else {
              selected.foreach{p=>
                val f = writer.next()
                f.setAttributes(Array[AnyRef](p._1, p._2, i.asInstanceOf[Integer]))
                writer.write()
              }
            }
            i += 1
            if (i % 1000 == 0) println(s"$i")
          }
        }
      }
    } finally reader.close()
  } finally store.dispose()
  if (!writeSeparate) {
    println(Calendar.getInstance.getTime + " done")
    writer.close()
    dataStore.dispose()
  }
}
