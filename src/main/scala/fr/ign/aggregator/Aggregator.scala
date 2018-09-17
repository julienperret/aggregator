package fr.ign.aggregator

import better.files.{File, Files}
import com.vividsolutions.jts.geom._
import com.vividsolutions.jts.operation.buffer.BufferParameters
import org.geotools.data.{DataUtilities, FeatureWriter, Transaction}
import org.geotools.data.shapefile.{ShapefileDataStore, ShapefileDataStoreFactory}
import org.geotools.geometry.jts.JTS
import org.geotools.referencing.CRS
import org.opengis.feature.simple.{SimpleFeature, SimpleFeatureType}

import scala.collection.mutable
import scala.util.Try

import fr.ign.aggregator.Utils.toPolygon
/**
  * Road agregation
  *
  * Created by julien on 28/08/17.
  */
object Aggregator extends App {
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
              writer.next.setAttributes(featureValues)
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
    val factory = new ShapefileDataStoreFactory
    val dataStore = factory.createDataStore(out.toJava.toURI.toURL)
    val featureTypeName = "Object"
    val featureType = DataUtilities.createType(featureTypeName, specs)
    dataStore.createSchema(featureType)
    val writer = dataStore.getFeatureWriterAppend(dataStore.getTypeNames()(0), Transaction.AUTO_COMMIT)
    System.setProperty("org.geotools.referencing.forceXY","true")
    for (elem <- files) readFile(elem, writer, values, filter)
    writer.close()
    dataStore.dispose()
  }
  def apply(dataDir: File, parcelDir: File, outputDir: File, buildings: Boolean, roads: Boolean, roadsSurface1:Boolean, roadsSurface2:Boolean, roadsSurface3: Boolean, rails: Boolean, rivers: Boolean, parcels: Boolean, isGroundTruth : Boolean) = {
    if (roads) {
      val childrenRoad = dataDir.collectChildren(f =>
        (f.pathAsString.contains("D077") || f.pathAsString.contains("D078") || f.pathAsString.contains("D091") || f.pathAsString.contains("D092") || f.pathAsString.contains("D093") ||
          f.pathAsString.contains("D094") || f.pathAsString.contains("D095")) && f.name.equalsIgnoreCase("ROUTE.SHP"))
      val outRoadFile = File(outputDir+"/roads_idf.shp")
      val specsRoad = "geom:MultiLineString:srid=2154,ID:String,NATURE:String,LARGEUR:Double,POS_SOL:Integer"

      def valuesRoad(feature: SimpleFeature) = {
        val default = feature.getDefaultGeometry.asInstanceOf[MultiLineString]
        val id = feature.getAttribute("ID").toString
        val nature = feature.getAttribute("NATURE").toString
        val largeur = feature.getAttribute("LARGEUR").asInstanceOf[Double]
        val pos = feature.getAttribute("POS_SOL").asInstanceOf[Int]
        Array[AnyRef](default, id, nature, largeur.asInstanceOf[AnyRef], pos.asInstanceOf[AnyRef])
      }

      val roadIds = new mutable.HashSet[String]

      def filterRoads(vals: Array[AnyRef]) = {
        val id = vals(1).asInstanceOf[String]
        val contains = roadIds.contains(id)
        if (!contains) roadIds.add(id)
        !contains && vals(4).asInstanceOf[Int] >= 0
      }

      aggregate(childrenRoad, outRoadFile, specsRoad, valuesRoad, filterRoads)
      println("roads done")
    }
    if (roadsSurface1) {
      val childrenSurfaceRoad = dataDir.collectChildren(f =>
        (f.pathAsString.contains("D077") || f.pathAsString.contains("D078") ||
          f.pathAsString.contains("D091") || f.pathAsString.contains("D092") || f.pathAsString.contains("D093") ||
          f.pathAsString.contains("D094") || f.pathAsString.contains("D095"))
          && f.name.equalsIgnoreCase("ROUTE.SHP"))
      val outRoadSurfaceFile = File(outputDir+"/roads_surface_idf.shp")
      val specsSurfaceRoad = "geom:MultiPolygon:srid=2154,ID:String,NATURE:String,LARGEUR:Double,POS_SOL:Integer"

      def valuesSurfaceRoad(feature: SimpleFeature) = {
        def defaultGeom = feature.getDefaultGeometry.asInstanceOf[Geometry]

        val id = feature.getAttribute("ID").toString
        val nature = feature.getAttribute("NATURE").toString
        val largeur = feature.getAttribute("LARGEUR").asInstanceOf[Double]
        val pos = feature.getAttribute("POS_SOL").asInstanceOf[Int]
        val surfaceGeom = defaultGeom.buffer(Math.max(2.0, largeur) / 2, 3, BufferParameters.CAP_FLAT)
        //NATURE:String,LARGEUR:Double,POS_SOL:Integer
        Array[AnyRef](surfaceGeom, id, nature, largeur.asInstanceOf[AnyRef], pos.asInstanceOf[AnyRef])
      }

      val roadSurfaceIds = new mutable.HashSet[String]

      def filterSurfaceRoads(vals: Array[AnyRef]) = {
        val id = vals(1).asInstanceOf[String]
        val contains = roadSurfaceIds.contains(id)
        if (!contains) roadSurfaceIds.add(id)
        !contains && vals(4).asInstanceOf[Int] >= 0
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
      val outBuildingsFile = File(outputDir+"/buildings_idf.shp")
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
        (f.pathAsString.contains("D077") || f.pathAsString.contains("D078") || f.pathAsString.contains("D091") || f.pathAsString.contains("D092") || f.pathAsString.contains("D093") ||
          f.pathAsString.contains("D094") || f.pathAsString.contains("D095")) && f.name.equalsIgnoreCase("TRONCON_VOIE_FERREE.SHP"))
      val outRailwayFile = File(outputDir+"/railway_surface_idf.shp")
      val specsRailway = "geom:MultiPolygon:srid=2154,ID:String,NATURE:String,LARGEUR:String,POS_SOL:Integer,NB_VOIES:Integer"

      def valuesRailway(feature: SimpleFeature) = {
        val default = feature.getDefaultGeometry.asInstanceOf[Geometry]
        val id = feature.getAttribute("ID").toString
        val nature = feature.getAttribute("NATURE").toString
        val largeur = feature.getAttribute("LARGEUR").toString
        val pos = feature.getAttribute("POS_SOL").asInstanceOf[Int]
        val nbVoies = feature.getAttribute("NB_VOIES").asInstanceOf[Int]
        val halfWidth = Math.max(2.0, nbVoies * 3.0) / 2
        Array[AnyRef](default.buffer(halfWidth, 3, BufferParameters.CAP_FLAT), id, nature, largeur, pos.asInstanceOf[AnyRef], nbVoies.asInstanceOf[AnyRef])
      }

      val railwayIds = new mutable.HashSet[String]

      def filterRailways(vals: Array[AnyRef]) = {
        val id = vals(1).asInstanceOf[String]
        val contains = railwayIds.contains(id)
        if (!contains) railwayIds.add(id)
        !contains && vals(4).asInstanceOf[Int] >= 0
      }

      aggregate(childrenRailway, outRailwayFile, specsRailway, valuesRailway, filterRailways)
      println("railway done")
    }
    if (rivers) {
      val childrenRivers = dataDir.collectChildren(f =>
        (f.pathAsString.contains("D077") || f.pathAsString.contains("D078") || f.pathAsString.contains("D091") || f.pathAsString.contains("D092") || f.pathAsString.contains("D093") ||
          f.pathAsString.contains("D094") || f.pathAsString.contains("D095")) && f.name.startsWith("SURFACE_EAU") && f.name.endsWith(".SHP"))
      val outRiversFile = File(outputDir+"/rivers_surface_idf.shp")
      val specsRivers = "geom:MultiPolygon:srid=2154"

      def valuesRivers(feature: SimpleFeature) = Array[AnyRef](toPolygon(feature.getDefaultGeometry))

      aggregate(childrenRivers, outRiversFile, specsRivers, valuesRivers)
      println("Rivers done")
    }
    if (parcels) {
      val childrenParcels = parcelDir.collectChildren(f => f.name.endsWith("parcelles.shp"))
      val outParcelFile = File(outputDir+"/parcels_idf.shp")


      var specsParcel = "";
      if(isGroundTruth){
         specsParcel = "geom:MultiPolygon:srid=2154,IDPAR:String,buildable:String"
      }else{
         specsParcel  = "geom:MultiPolygon:srid=2154,IDPAR:String"
      }
      val (inCRS, outCRS) = (CRS.decode("EPSG:2154"), CRS.decode("EPSG:2154"))
      val transform = CRS.findMathTransform(inCRS, outCRS, true)

      def valuesParcel(feature: SimpleFeature) = {
        if(isGroundTruth){
           Array[AnyRef](JTS.transform(toPolygon(feature.getDefaultGeometry), transform), feature.getAttribute("id").toString,  feature.getAttribute("buildable").toString)

        }else{
          Array[AnyRef](JTS.transform(toPolygon(feature.getDefaultGeometry), transform), feature.getAttribute("id").toString)
        }


      }
      aggregate(childrenParcels, outParcelFile, specsParcel, valuesParcel)
      println("parcels done")
    }
  }

  //BD Topo Folder
  val dataDir = File("/home/mbrasebin/Documents/Donnees/BDTopo/94")
  //Cadastre folder
  val parcelDir = File("/home/mbrasebin/Documents/Donnees/IAUIDF/Classification/ground_truth")
  //Absolute path to output directory
  val outDir = File("/home/mbrasebin/Documents/Donnees/IAUIDF/Classification/ground_truth/out")
  outDir.createDirectories()
  //Activate or not the generation of the different elements
  val buildings = true
  val roads = true
  val roadsSurface1 = true
  val roadsSurface2 = true
  val roadsSurface3 = true
  val rails = true
  val rivers = true
  val parcels = true
  //Report buildable attribute if isGroundTruth
  val isGroundTruth = true
  Aggregator(dataDir, parcelDir, outDir, buildings, roads, roadsSurface1, roadsSurface2, roadsSurface3, rails, rivers, parcels, isGroundTruth)
}
