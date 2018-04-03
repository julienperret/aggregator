package fr.ign.aggregator

import better.files.{File, Files}
import com.vividsolutions.jts.geom._
import com.vividsolutions.jts.geom.util.AffineTransformation
import com.vividsolutions.jts.operation.buffer.BufferParameters
import org.geotools.data.{DataUtilities, FeatureWriter, Transaction}
import org.geotools.data.shapefile.{ShapefileDataStore, ShapefileDataStoreFactory}
import org.geotools.geometry.jts.JTS
import org.geotools.referencing.CRS
import org.opengis.feature.simple.{SimpleFeature, SimpleFeatureType}

import scala.collection.mutable
import scala.util.Try

/**
  * Road agregation
  *
  * Created by julien on 28/08/17.
  */
object Aggregator extends App {
  val dataDir = File("/home/julien/data/bdtopo2017")
  val parcelsDir = File("/home/julien/data/cadastre")
  val buildings = false
  val roads = false
  val roadsSurface = true
  val rails = false
  val rivers = false
  val parcels = false

  def orientedMinimumBoundingBox(g:Geometry): (Geometry,Double,Double,Double,Double) = {
    var area = Double.MaxValue
    var angle = 0.0
    var width = Double.MaxValue
    var height = Double.MaxValue
    var minRect: Option[Geometry] = None
    val emptyResult = (g.getFactory.createGeometryCollection(Array[Geometry]()), 0.0, 0.0, 0.0, 0.0)
    if (g.getNumPoints < 2) emptyResult
    else {
      var hull = g.convexHull()
      if (hull.isEmpty) emptyResult
      else {
        // get first point
        val coordinates = hull.getCoordinates
        val pt0 = coordinates(0)
        val pt1 = pt0
        var prevAngle = 0.0
        for (i <- 1 until hull.getNumPoints) {
          var pt2 = coordinates(i)
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
    println("Read: " + aFile)
    val store = new ShapefileDataStore(aFile.toJava.toURI.toURL)
    var i = 0
    try {
      val reader = store.getFeatureReader
      try {
        Try {
          val featureReader = Iterator.continually(reader.next).takeWhile(_ => reader.hasNext)
          featureReader.foreach { feature =>
            val featureValues = values(feature)
            if (filter(featureValues)) {
              val simpleFeature = writer.next
              simpleFeature.setAttributes(featureValues)
              writer.write()
              i += 1
            }
          }
        }
      } finally reader.close()
    } finally store.dispose()
    println("added " + i + " features")
  }
  def aggregate(files:Files, out:File, specs:String, values: SimpleFeature=>Array[AnyRef], filter: Array[AnyRef]=>Boolean = _=> true) = {
    //val geometryFactory = new GeometryFactory
    val factory = new ShapefileDataStoreFactory
    val dataStore = factory.createDataStore(out.toJava.toURI.toURL)
    val featureTypeName = "Object"
    val featureType = DataUtilities.createType(featureTypeName, specs)
    dataStore.createSchema(featureType)
    val typeName = dataStore.getTypeNames()(0)
    val writer = dataStore.getFeatureWriterAppend(typeName, Transaction.AUTO_COMMIT)
    System.setProperty("org.geotools.referencing.forceXY","true")
    for (elem <- files) readFile(elem, writer, values, filter)
    writer.close()
    dataStore.dispose()
  }

  if (roads) {
    val childrenRoad = dataDir.collectChildren(f =>
      (f.pathAsString.contains("D077") || f.pathAsString.contains("D078") ||
        f.pathAsString.contains("D091") || f.pathAsString.contains("D092") || f.pathAsString.contains("D093") ||
        f.pathAsString.contains("D094") || f.pathAsString.contains("D095"))
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

    aggregate(childrenRoad, outRoadFile, specsRoad, valuesRoad, filterRoads)
    println("roads done")
  }
  if (roadsSurface) {
    val childrenSurfaceRoad = dataDir.collectChildren(f =>
      (f.pathAsString.contains("D077") || f.pathAsString.contains("D078") ||
        f.pathAsString.contains("D091") || f.pathAsString.contains("D092") || f.pathAsString.contains("D093") ||
        f.pathAsString.contains("D094") || f.pathAsString.contains("D095"))
        && f.name.equalsIgnoreCase("ROUTE.SHP"))
    val outRoadSurfaceFile = File("roads_surface_elongation_idf.shp")
    val specsSurfaceRoad = "geom:MultiPolygon:srid=2154,ID:String,NATURE:String,LARGEUR:Double,POS_SOL:Integer,WIDTH:Double,HEIGHT:Double,ELONGATION:Double"

    def valuesSurfaceRoad(feature: SimpleFeature) = {
      //val default = cast( feature.getDefaultGeometry )
      def defaultGeom = feature.getDefaultGeometry.asInstanceOf[Geometry]
      val id = feature.getAttribute("ID").toString
      val nature = feature.getAttribute("NATURE").toString
      val largeur = feature.getAttribute("LARGEUR").asInstanceOf[Double]
      val pos = feature.getAttribute("POS_SOL").asInstanceOf[Int]
      val surfaceGeom = defaultGeom.buffer(Math.max(1.0, largeur) / 2, 3, BufferParameters.CAP_FLAT)
      val (_, _, _, width, height) = orientedMinimumBoundingBox(surfaceGeom)
      val min = Math.min(width, height)
      val max = Math.max(width, height)
      val elongation = min / max
      //NATURE:String,LARGEUR:Double,POS_SOL:Integer
      Array[AnyRef](
        surfaceGeom,
        id,
        nature,
        largeur.asInstanceOf[AnyRef],
        pos.asInstanceOf[AnyRef],
        min.asInstanceOf[AnyRef],
        max.asInstanceOf[AnyRef],
        elongation.asInstanceOf[AnyRef]
      )
    }

    val roadSurfaceIds = new mutable.HashSet[String]

    def filterSurfaceRoads(vals: Array[AnyRef]) = {
      val id = vals(1).asInstanceOf[String]
      val contains = roadSurfaceIds.contains(id)
      if (!contains) roadSurfaceIds.add(id)
      !contains
    }

    aggregate(childrenSurfaceRoad, outRoadSurfaceFile, specsSurfaceRoad, valuesSurfaceRoad, filterSurfaceRoads)
    println("roads Surface done")
  }
  if (buildings) {
    val childrenBuildings = dataDir.collectChildren(f =>
      (f.pathAsString.contains("D077") || f.pathAsString.contains("D078") ||
        f.pathAsString.contains("D091") || f.pathAsString.contains("D092") || f.pathAsString.contains("D093") ||
        f.pathAsString.contains("D094") || f.pathAsString.contains("D095"))
        && f.name.startsWith("BATI_") && f.name.endsWith(".SHP"))
    val outBuildingsFile = File("buildings_idf.shp")
    val specsBuildings = "geom:MultiPolygon:srid=2154"

    def valuesBuildings(feature: SimpleFeature) = {
      val default = feature.getDefaultGeometry.asInstanceOf[MultiPolygon]
      Array[AnyRef](
        default
      )
    }

    aggregate(childrenBuildings, outBuildingsFile, specsBuildings, valuesBuildings)
    println("Buildings done")
  }

  if (rails) {
    val childrenRailway = dataDir.collectChildren(f =>
      (f.pathAsString.contains("D077") || f.pathAsString.contains("D078") ||
        f.pathAsString.contains("D091") || f.pathAsString.contains("D092") || f.pathAsString.contains("D093") ||
        f.pathAsString.contains("D094") || f.pathAsString.contains("D095"))
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
        default.buffer(Math.max(1.0, nbVoies * 3.0) / 2, 3, BufferParameters.CAP_FLAT),
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

    aggregate(childrenRailway, outRailwayFile, specsRailway, valuesRailway, filterRailways)
    println("railway done")
  }
  if (rivers) {
    val childrenRivers = dataDir.collectChildren(f =>
      (f.pathAsString.contains("D077") || f.pathAsString.contains("D078") ||
        f.pathAsString.contains("D091") || f.pathAsString.contains("D092") || f.pathAsString.contains("D093") ||
        f.pathAsString.contains("D094") || f.pathAsString.contains("D095"))
        && f.name.startsWith("SURFACE_EAU") && f.name.endsWith(".SHP"))
    val outRiversFile = File("rivers_idf.shp")
    val specsRivers = "geom:MultiPolygon:srid=2154"

    def valuesRivers(feature: SimpleFeature) = {
      val default = feature.getDefaultGeometry.asInstanceOf[MultiPolygon]
      Array[AnyRef](
        default
      )
    }

    aggregate(childrenRivers, outRiversFile, specsRivers, valuesRivers)
    println("Rivers done")
  }
  if (parcels) {
    val childrenParcels = parcelsDir.collectChildren(f=> f.name.endsWith("parcelles.shp"))
    val outParcelFile = File("parcels_idf.shp")
    val specsParcel = "geom:MultiPolygon:srid=2154,IDPAR:String,WIDTH:Double,HEIGHT:Double,ELONGATION:Double"
    val inCRS = CRS.decode("EPSG:4326")
    val outCRS = CRS.decode("EPSG:2154")
    val transform = CRS.findMathTransform(inCRS, outCRS, true)

    def valuesParcel(feature: SimpleFeature) = {
      def defaultGeom = feature.getDefaultGeometry.asInstanceOf[MultiPolygon]
      val transformed = JTS.transform(defaultGeom, transform)
      val parcelId = feature.getAttribute("id").toString
      val (_,_,_,width,height) = orientedMinimumBoundingBox(transformed)
      val min = Math.min(width,height)
      val max = Math.max(width,height)
      val elongation = min / max
      Array[AnyRef](
        transformed,
        parcelId,
        min.asInstanceOf[AnyRef],
        max.asInstanceOf[AnyRef],
        elongation.asInstanceOf[AnyRef]
      )
    }
    aggregate(childrenParcels,outParcelFile,specsParcel,valuesParcel)
    println("parcels done")
  }
}
