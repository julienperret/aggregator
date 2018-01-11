package fr.ign.aggregator

import java.util.Calendar

import better.files.File
import com.vividsolutions.jts.geom.{GeometryFactory, MultiPolygon, PrecisionModel}
import com.vividsolutions.jts.index.strtree.STRtree
import com.vividsolutions.jts.operation.union.CascadedPolygonUnion
import com.vividsolutions.jts.precision.GeometryPrecisionReducer
import org.geotools.data.{DataUtilities, FeatureWriter, Transaction}
import org.geotools.data.shapefile.{ShapefileDataStore, ShapefileDataStoreFactory}
import org.geotools.geometry.jts.JTS
import org.opengis.feature.simple.{SimpleFeature, SimpleFeatureType}
import org.opengis.referencing.operation.MathTransform

import scala.util.Try

object ComputeMeasures extends App {
  def index(aFile: File, filter: SimpleFeature=>Boolean = _=>true) = {
    val store = new ShapefileDataStore(aFile.toJava.toURI.toURL)
    val index = new STRtree()
    try {
      val reader = store.getFeatureReader
      try {
        Try {
          val featureReader = Iterator.continually(reader.next).takeWhile(_ => reader.hasNext)
          featureReader.foreach { feature =>
            if (filter(feature)) {
              val geom = feature.getDefaultGeometry.asInstanceOf[MultiPolygon]
              index.insert(geom.getEnvelopeInternal, geom)
            }
          }
        }
      } finally reader.close
    } finally store.dispose
    index
  }
  import scala.collection.JavaConverters._
  def intersection(geom: MultiPolygon, index: STRtree) = {
    val r = Try {
      val intersects = index.query(geom.getEnvelopeInternal).toArray.toSeq.map(_.asInstanceOf[MultiPolygon]).filter(_.intersects(geom))
      val intersections = intersects.map(_.intersection(geom))
      if (intersections.isEmpty) None
      else Some(geom.getFactory.createGeometryCollection(intersections.toArray).union)
      //else Some(CascadedPolygonUnion.union(intersections.toList.asJava))
    }
    if (r.isFailure) {
      println("failure on " + geom)
      val intersects = index.query(geom.getEnvelopeInternal).toArray.toSeq.map(_.asInstanceOf[MultiPolygon]).filter(_.intersects(geom))
      val intersections = intersects.map(_.intersection(geom))
      intersections.foreach(println(_))
      //println(gc)
      r.failed.get.printStackTrace
      val gpr = new GeometryPrecisionReducer(new PrecisionModel(1000))
      val gc = geom.getFactory.createGeometryCollection(intersections.toArray.map(gpr.reduce(_)))
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
                          buildingIndex: STRtree) = {
    val store = new ShapefileDataStore(aFile.toJava.toURI.toURL)

    var i = 0
    try {
      val reader = store.getFeatureReader
      try {
        Try {
          val featureReader = Iterator.continually(reader.next).takeWhile(_ => reader.hasNext)
          featureReader.foreach { feature =>
            val default = feature.getDefaultGeometry.asInstanceOf[MultiPolygon]
            val area = default.getArea
            val roadArea = intersection(default, roadIndex)
            val roadRatio = roadArea / area
            val railwayArea = intersection(default, railwayIndex)
            val railwayRatio = railwayArea / area
            val buildingArea = intersection(default, buildingIndex)
            val buildingRatio = buildingArea / area
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
              buildingRatio.asInstanceOf[AnyRef]
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

  val folder = "/home/mbrasebin/Bureau/Data_Fin/"

  val roadFile = File(folder+"roads_surface_elongation_idf.shp")
  val buildingsFile = File(folder+"buildings_idf.shp")
  val railwayFile = File(folder+"railway_surface_idf.shp")
  val parcelFile = File(folder+"parcels_idf.shp")

  val specs = "geom:MultiPolygon:srid=2154,IDPAR:String,WIDTH:Double,HEIGHT:Double,ELONGATION:Double,roadArea:Double,roadRatio:Double,railArea:Double,railRatio:Double,buildArea:Double,buildRatio:Double"
  val out = File(folder+"parcels_measures_idf_2.shp")
  println(Calendar.getInstance.getTime + " loading index")
  val roadIndex = index(roadFile,f=>f.getAttribute("POS_SOL").asInstanceOf[Int]>=0)
  println(Calendar.getInstance.getTime + " loading index")
  val railIndex = index(railwayFile,f=>f.getAttribute("POS_SOL").asInstanceOf[Int]>=0)
  println(Calendar.getInstance.getTime + " loading index")
  val buildIndex = index(buildingsFile)
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
  readAndWriteParcels(parcelFile,writer,roadIndex,railIndex,buildIndex)
  println(Calendar.getInstance.getTime + " done")
  writer.close
  dataStore.dispose

}
