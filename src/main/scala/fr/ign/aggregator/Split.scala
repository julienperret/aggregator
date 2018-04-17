package fr.ign.aggregator

import better.files.File
import com.vividsolutions.jts.geom._
import com.vividsolutions.jts.index.strtree.STRtree
import com.vividsolutions.jts.precision.GeometryPrecisionReducer
import org.geotools.data.{DataUtilities, Transaction}
import org.geotools.data.shapefile.{ShapefileDataStore, ShapefileDataStoreFactory}
import org.geotools.geometry.jts.GeometryClipper

import scala.util.Try

import Utils._

object Split extends App {
  def aggregate(in:File, out:File, specs:String) = {
    val factory = new ShapefileDataStoreFactory
    val dataStore = factory.createDataStore(out.toJava.toURI.toURL)
    val featureTypeName = "Object"
    val featureType = DataUtilities.createType(featureTypeName, specs)
    dataStore.createSchema(featureType)
    val typeName = dataStore.getTypeNames()(0)
    val writer = dataStore.getFeatureWriterAppend(typeName, Transaction.AUTO_COMMIT)
    System.setProperty("org.geotools.referencing.forceXY","true")
    val ind = indexPolygon(in)
    val bounds = ind.getRoot.getBounds.asInstanceOf[Envelope]
    val geomFactory = new GeometryFactory()
    val gpr = new GeometryPrecisionReducer(new PrecisionModel(1000))
    var i = 0
    envelopes(bounds, 100.0).foreach{
      e=> {
        val clipper = new GeometryClipper(e)
        val v = ind.query(e).toArray.map(toPolygon).map(clipper.clipSafe(_, true, 1000.0)).filter(_.isValid).map(gpr.reduce)
        val g = geomFactory.createGeometryCollection(v).buffer(0.0)
        if (g.isValid) {
          val simpleFeature = writer.next
          simpleFeature.setDefaultGeometry(g)
          writer.write()
          i += 1
          if (i % 100 == 0) println(i)
        }
      }
    }
    writer.close()
    dataStore.dispose()
  }
  val inRoadFile = File("roads_surface_elongation_idf.shp")
  val outRoadFile = File("roads.shp")
  val specs = "geom:MultiPolygon:srid=2154"
  aggregate(inRoadFile, outRoadFile, specs)
  println("done")
}
