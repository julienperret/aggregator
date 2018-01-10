package fr.ign.aggregator

import better.files.{File, Files}
import com.vividsolutions.jts.geom._
import com.vividsolutions.jts.geom.util.AffineTransformation
import com.vividsolutions.jts.operation.buffer.BufferParameters
import org.geotools.data.{DataUtilities, FeatureWriter, Transaction}
import org.geotools.data.shapefile.{ShapefileDataStore, ShapefileDataStoreFactory}
import org.opengis.feature.simple.{SimpleFeature, SimpleFeatureType}

import scala.collection.mutable
import scala.util.Try

/**
  * Created by julien on 28/08/17.
  */
object Aggregator extends App {

  def orientedMinimumBoundingBox(g:Geometry): (Geometry,Double,Double,Double,Double) = {
    var area = Double.MaxValue
    var angle = 0.0
    var width = Double.MaxValue
    var height = Double.MaxValue
    var minRect: Option[Geometry] = None
    val emptyres = (g.getFactory.createGeometryCollection(Array[Geometry]()), 0.0, 0.0, 0.0, 0.0)
    if (g.getNumPoints < 2) emptyres
    else {
      var hull = g.convexHull()
      if (hull.isEmpty) emptyres
      else {
        // get first point
        val coords = hull.getCoordinates
        val pt0 = coords(0)
        val pt1 = pt0
        var prevAngle = 0.0
        for (i <- (1 until hull.getNumPoints)) {
          var pt2 = coords(i)
          val currentAngle = new LineSegment(pt1.x, pt1.y, pt2.x, pt2.y).angle
          val rotateAngle = 180.0 / Math.PI * (currentAngle - prevAngle)
          prevAngle = currentAngle
          var t = AffineTransformation.translationInstance(pt0.x, pt0.y)
          t = t.rotate(rotateAngle)
          t = t.translate(-pt0.x, -pt0.y)
          hull = t.transform(hull)
          val bounds = hull.getEnvelopeInternal
          val currentArea = bounds.getWidth * bounds.getHeight
          if (currentArea < area) {
            minRect = Some(hull.getEnvelope)
            area = currentArea
            angle = 180.0 / Math.PI * currentAngle
            width = bounds.getWidth
            height = bounds.getHeight
          }
          pt2 = pt1
        }
        var minBounds = minRect.get
        val r = AffineTransformation.rotationInstance(angle, pt0.x, pt0.y)
        minBounds = r.transform(minBounds)
        // constrain angle to 0 - 180
        if (angle > 180.0) angle = angle % 180.0
        (minBounds, area, angle, width, height)
      }
    }
  }
  def readFile(aFile: File, writer:FeatureWriter[SimpleFeatureType, SimpleFeature], values: SimpleFeature=>Array[AnyRef], filter: Array[AnyRef]=>Boolean) = {
    println(aFile)
    val store = new ShapefileDataStore(aFile.toJava.toURI.toURL)
    var i = 0
    try {
      val reader = store.getFeatureReader
      try {
        Try {
          val featureReader = Iterator.continually(reader.next).takeWhile(_ => reader.hasNext)
          featureReader.foreach { feature =>
            val vals = values(feature)
            if (filter(vals)) {
              val simpleFeature = writer.next
              simpleFeature.setAttributes(vals)
              writer.write
              i += 1
            }
          }
        }
      } finally reader.close
    } finally store.dispose
    println("added " + i + " features")
  }
  def aggregate(files:Files, out:File, specs:String, values: SimpleFeature=>Array[AnyRef], filter: Array[AnyRef]=>Boolean = _=> true) = {
    val geometryFactory = new GeometryFactory
    val factory = new ShapefileDataStoreFactory
    val dataStore = factory.createDataStore(out.toJava.toURI.toURL)
    val featureTypeName = "Object"
    val featureType = DataUtilities.createType(featureTypeName, specs)
    dataStore.createSchema(featureType)
    val typeName = dataStore.getTypeNames()(0)
    val writer = dataStore.getFeatureWriterAppend(typeName, Transaction.AUTO_COMMIT)
    System.setProperty("org.geotools.referencing.forceXY","true")
    for (elem <- files) readFile(elem, writer, values, filter)
    writer.close
    dataStore.dispose
  }

  val childrenRoad = File("/home/julien/data/dpsg/dpsg2017-06-00426/BDTOPO/1_DONNEES_LIVRAISON_2017-06-00426").
    collectChildren(f=>
      (f.pathAsString.contains("D077")||f.pathAsString.contains("D078")||
        f.pathAsString.contains("D091")||f.pathAsString.contains("D092")||f.pathAsString.contains("D093")||
        f.pathAsString.contains("D094")||f.pathAsString.contains("D095"))
        && f.name.equalsIgnoreCase("ROUTE.SHP"))
  val outRoadFile = File("roads_idf.shp")
  val specsRoad = "geom:MultiLineString:srid=2154,ID:String,NATURE:String,LARGEUR:Double,POS_SOL:Integer"
  def valuesRoad(feature: SimpleFeature) = {
    val default = feature.getDefaultGeometry.asInstanceOf[MultiLineString]
    val id = feature.getAttribute("ID").toString
    val nature = feature.getAttribute("NATURE").toString
    val largeur = feature.getAttribute("LARGEUR").asInstanceOf[Double]
    val pos = feature.getAttribute("POS_SOL").asInstanceOf[Int]
    Array[AnyRef](
      default,
      id,
      nature,
      largeur.asInstanceOf[AnyRef],
      pos.asInstanceOf[AnyRef]
    )
  }
  val roadIds = new mutable.HashSet[String]
  def filterRoads(vals: Array[AnyRef]) = {
    val id = vals(1).asInstanceOf[String]
    val contains = roadIds.contains(id)
    if (!contains) roadIds.add(id)
    !contains
  }
  aggregate(childrenRoad,outRoadFile,specsRoad,valuesRoad,filterRoads)
  println("roads done")

  /*
  val outRoadFile = File("roads_surface_elongation_idf.shp")
  val specsRoad = "geom:MultiPolygon:srid=2154,ID:String,NATURE:String,LARGEUR:Double,POS_SOL:Integer,WIDTH:Double,HEIGHT:Double,ELONGATION:Double"
  def valuesRoad(feature: SimpleFeature) = {
    //val default = cast( feature.getDefaultGeometry )
    val default = feature.getDefaultGeometry.asInstanceOf[Geometry]
    val id = feature.getAttribute("ID").toString
    val nature = feature.getAttribute("NATURE").toString
    val largeur = feature.getAttribute("LARGEUR").asInstanceOf[Double]
    val pos = feature.getAttribute("POS_SOL").asInstanceOf[Int]
    val (ombb,area,angle,width,height) = orientedMinimumBoundingBox(default)
    val min = Math.min(width,height)
    val max = Math.max(width,height)
    val elongation = min / max
    //NATURE:String,LARGEUR:Double,POS_SOL:Integer
    Array[AnyRef](
      default.buffer(Math.max(1.0,largeur) / 2, 3, BufferParameters.CAP_FLAT),
      id,
      nature,
      largeur.asInstanceOf[AnyRef],
      pos.asInstanceOf[AnyRef],
      min.asInstanceOf[AnyRef],
      max.asInstanceOf[AnyRef],
      elongation.asInstanceOf[AnyRef]
    )
  }
  val roadIds = new mutable.HashSet[String]
  def filterRoads(vals: Array[AnyRef]) = {
    val id = vals(1).asInstanceOf[String]
    val contains = roadIds.contains(id)
    if (!contains) roadIds.add(id)
    !contains
  }
  aggregate(childrenRoad,outRoadFile,specsRoad,valuesRoad,filterRoads)
  println("roads done")
  */
  /*
  val childrenBuildings = File("/home/julien/data/dpsg/dpsg2017-06-00426/BDTOPO/1_DONNEES_LIVRAISON_2017-06-00426").
    collectChildren(f=>
      (f.pathAsString.contains("D077")||f.pathAsString.contains("D078")||
        f.pathAsString.contains("D091")||f.pathAsString.contains("D092")||f.pathAsString.contains("D093")||
        f.pathAsString.contains("D094")||f.pathAsString.contains("D095"))
        && f.name.startsWith("BATI_") && f.name.endsWith(".SHP"))
  val outBuildingsFile = File("buildings_idf.shp")
  val specsBuildings = "geom:MultiPolygon:srid=2154"
  def valuesBuildings(feature: SimpleFeature) = {
    val default = feature.getDefaultGeometry.asInstanceOf[MultiPolygon]
    Array[AnyRef](
      default
    )
  }
  aggregate(childrenBuildings,outBuildingsFile,specsBuildings,valuesBuildings)
  println("Buildings done")
  val childrenParcels = File("/home/julien/data/iauidf").collectChildren(f=>(f.name.equalsIgnoreCase("parcelles.shp")))
  val outParcelFile = File("parcels_idf.shp")
  val specsParcel = "geom:MultiPolygon:srid=2154,IDPAR:String,WIDTH:Double,HEIGHT:Double,ELONGATION:Double"
  def valuesParcel(feature: SimpleFeature) = {
    val default = feature.getDefaultGeometry.asInstanceOf[MultiPolygon]
    val idpar = feature.getAttribute("IDPAR").toString
    val (ombb,area,angle,width,height) = orientedMinimumBoundingBox(default)
    val min = Math.min(width,height)
    val max = Math.max(width,height)
    val elongation = min / max
    Array[AnyRef](
      default,
      idpar,
      min.asInstanceOf[AnyRef],
      max.asInstanceOf[AnyRef],
      elongation.asInstanceOf[AnyRef]
    )
  }
  aggregate(childrenParcels,outParcelFile,specsParcel,valuesParcel)
  println("parcels done")

  val childrenRailway = File("/home/julien/data/dpsg/dpsg2017-06-00426/BDTOPO/1_DONNEES_LIVRAISON_2017-06-00426").
    collectChildren(f=>
      (f.pathAsString.contains("D077")||f.pathAsString.contains("D078")||
        f.pathAsString.contains("D091")||f.pathAsString.contains("D092")||f.pathAsString.contains("D093")||
        f.pathAsString.contains("D094")||f.pathAsString.contains("D095"))
        && f.name.equalsIgnoreCase("TRONCON_VOIE_FERREE.SHP"))
  val outRailwayFile = File("railway_surface_idf.shp")
  val specsRailway = "geom:MultiPolygon:srid=2154,ID:String,NATURE:String,LARGEUR:String,POS_SOL:Integer,NB_VOIES:Integer"
  def valuesRailway(feature: SimpleFeature) = {
    val default = feature.getDefaultGeometry.asInstanceOf[Geometry]
    val id = feature.getAttribute("ID").toString
    val nature = feature.getAttribute("NATURE").toString
    val largeur = feature.getAttribute("LARGEUR").toString
    val pos = feature.getAttribute("POS_SOL").asInstanceOf[Int]
    val nbVoies = feature.getAttribute("NB_VOIES").asInstanceOf[Int]
    Array[AnyRef](
      default.buffer(Math.max(1.0,nbVoies * 3.0) / 2, 3, BufferParameters.CAP_FLAT),
      id,
      nature,
      largeur,
      pos.asInstanceOf[AnyRef],
      nbVoies.asInstanceOf[AnyRef]
    )
  }
  val railwayIds = new mutable.HashSet[String]
  def filterRailways(vals: Array[AnyRef]) = {
    val id = vals(1).asInstanceOf[String]
    val contains = railwayIds.contains(id)
    if (!contains) railwayIds.add(id)
    !contains
  }
  aggregate(childrenRailway,outRailwayFile,specsRailway,valuesRailway,filterRailways)
  println("railway done")
*/
}
