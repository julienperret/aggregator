package fr.ign.aggregator

import java.util.Calendar

import better.files.File
import com.vividsolutions.jts.geom._
import com.vividsolutions.jts.index.strtree.STRtree
import com.vividsolutions.jts.precision.GeometryPrecisionReducer
import org.geotools.data.{DataUtilities, FeatureWriter, Transaction}
import org.geotools.data.shapefile.{ShapefileDataStore, ShapefileDataStoreFactory}
import org.opengis.feature.simple.{SimpleFeature, SimpleFeatureType}

import scala.util.Try

import Utils._

object ComputeMeasures extends App {
  val gpr = new GeometryPrecisionReducer(new PrecisionModel(1000))
  def intersection(geom: MultiPolygon, index: STRtree) = {
    val r = Try {
      val intersects = index.query(geom.getEnvelopeInternal).toArray.toSeq.map(toPolygon).map(gpr.reduce).map(toPolygon).filter(_.intersects(geom))
      val intersections = intersects.map(_.intersection(geom)).map(gpr.reduce)
      if (intersections.isEmpty) None
      else Some(geom.getFactory.createGeometryCollection(intersections.toArray).union)
      //else Some(CascadedPolygonUnion.union(intersections.toList.asJava))
    }
    if (r.isFailure) {
      println("failure on " + geom)
      val intersects = index.query(geom.getEnvelopeInternal).toArray.toSeq.map(toPolygon).map(gpr.reduce).map(toPolygon).filter(_.intersects(geom))
      println(intersects.size + " intersect")
      val intersections = intersects.map(_.intersection(geom)).map(gpr.reduce)
      println(intersections.size + " intersections")
      intersections.foreach(println(_))
      //println(gc)
      r.failed.get.printStackTrace()
      val gc = geom.getFactory.createGeometryCollection(intersections.toArray)
      val rr = Try {
        val union = gc.union
        union
      }
      if (rr.isFailure) {
        println("i give up")
        0.0
      } else {
        rr.map(_.getArea).getOrElse(0.0)
      }
    } else {
      r.get.map(_.getArea).getOrElse(0.0)
    }
  }

  def readAndWriteParcels(aFile: File,
                          writer: FeatureWriter[SimpleFeatureType, SimpleFeature],
                          roadIndex: STRtree,
                          railwayIndex: STRtree,
                          buildingIndex: STRtree,
                          riverIndex: STRtree) = {
    val store = new ShapefileDataStore(aFile.toJava.toURI.toURL)
    var i = 0
    try {
      val reader = store.getFeatureReader
      try {
        Try {
          val featureReader = Iterator.continually(reader.next).takeWhile(_ => reader.hasNext)
          featureReader.foreach { feature =>
            val default = feature.getDefaultGeometry.asInstanceOf[MultiPolygon].buffer(0.0)
            val cleanGeom = toPolygon(gpr.reduce(default))
            val area = default.getArea
            val roadArea = intersection(cleanGeom, roadIndex)
            val roadRatio = roadArea / area
            val railwayArea = intersection(cleanGeom, railwayIndex)
            val railwayRatio = railwayArea / area
            val buildingArea = intersection(cleanGeom, buildingIndex)
            val buildingRatio = buildingArea / area
            val riverArea = intersection(cleanGeom, riverIndex)
            val riverRatio = riverArea / area
            val id = feature.getAttribute("IDPAR").toString
            val width = feature.getAttribute("WIDTH").asInstanceOf[Double]
            val height = feature.getAttribute("HEIGHT").asInstanceOf[Double]
            val elongation = feature.getAttribute("ELONGATION").asInstanceOf[Double]
            val values = Array[AnyRef](
              default,
              id,
              width.asInstanceOf[AnyRef],
              height.asInstanceOf[AnyRef],
              elongation.asInstanceOf[AnyRef],
              roadArea.asInstanceOf[AnyRef],
              roadRatio.asInstanceOf[AnyRef],
              railwayArea.asInstanceOf[AnyRef],
              railwayRatio.asInstanceOf[AnyRef],
              buildingArea.asInstanceOf[AnyRef],
              buildingRatio.asInstanceOf[AnyRef],
              riverArea.asInstanceOf[AnyRef],
              riverRatio.asInstanceOf[AnyRef]
            )
            val simpleFeature = writer.next
            simpleFeature.setAttributes(values)
            writer.write()
            i+=1
          }
        }
      } finally reader.close()
    } finally store.dispose()
    println("added " + i + " features")
  }

  //Aggregator folder
  val folder = File("/home/mbrasebin/Documents/Donnees/IAUIDF/Classification")
  folder.createDirectories()
  val roadFile = folder / "roads_surface_idf.shp"
  val buildingsFile = folder / "buildings_idf.shp"
  val railwayFile = folder / "railway_surface_idf.shp"
  val riversFile = folder / "rivers_surface_idf.shp"
  val parcelFile = folder / "parcels_idf.shp"

  val specs = "geom:MultiPolygon:srid=2154,IDPAR:String,WIDTH:Double,HEIGHT:Double,ELONGATION:Double,roadArea:Double,roadRatio:Double,railArea:Double,railRatio:Double,buildArea:Double,buildRatio:Double,riverArea:Double,riverRatio:Double"
  val out = folder / "parcels_measures_idf.shp"
  println(Calendar.getInstance.getTime + " loading index")
  val roadIndex = indexPolygon(roadFile, f=>f.getAttribute("POS_SOL").asInstanceOf[Int]>=0)
  println(Calendar.getInstance.getTime + " loading index")
  val railIndex = indexPolygon(railwayFile, f=>f.getAttribute("POS_SOL").asInstanceOf[Int]>=0)
  println(Calendar.getInstance.getTime + " loading index")
  val riverIndex = indexPolygon(riversFile)
  println(Calendar.getInstance.getTime + " loading index")
  val buildIndex = indexPolygon(buildingsFile)
  println(Calendar.getInstance.getTime + " loading index")
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
  readAndWriteParcels(parcelFile,writer,roadIndex,railIndex,buildIndex,riverIndex)
  println(Calendar.getInstance.getTime + " done")
  writer.close()
  dataStore.dispose()
}
