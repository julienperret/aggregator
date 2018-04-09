package fr.ign.aggregator

import java.util.Calendar

import better.files.File
import com.vividsolutions.jts.geom.GeometryFactory
import org.geotools.data.shapefile.{ShapefileDataStore, ShapefileDataStoreFactory}
import org.opengis.feature.simple.SimpleFeature
import smile.classification.{gbm, _}

import scala.collection.mutable
import scala.util.Try

object ClassifySmile extends App {
  abstract class ClassifierType
  case class DecisionTree(maxNodes: Int = 100) extends ClassifierType
  case class GradientBoostedTrees() extends ClassifierType
  case class AdaBoost() extends ClassifierType

  def attributes(feature: SimpleFeature) = {
    val roadRatio = feature.getAttribute("roadRatio").asInstanceOf[Double]
    val roadArea = feature.getAttribute("roadArea").asInstanceOf[Double]
    //println(roadRatio)
    val railwayRatio = feature.getAttribute("railRatio").asInstanceOf[Double]
    val railwayArea = feature.getAttribute("railArea").asInstanceOf[Double]
    //println(railwayRatio)
    val buildingRatio = feature.getAttribute("buildRatio").asInstanceOf[Double]
    val buildingArea = feature.getAttribute("buildArea").asInstanceOf[Double]
    //println(buildingRatio)
    val riverRatio = feature.getAttribute("riverRatio").asInstanceOf[Double]
    val riverArea = feature.getAttribute("riverArea").asInstanceOf[Double]
    //println(riverRatio)
    val elongation = feature.getAttribute("ELONGATION").asInstanceOf[Double]
    //println(elongation)
    Array(roadRatio, roadArea, railwayRatio, railwayArea, buildingRatio, buildingArea, riverRatio, riverArea, elongation)
  }
  def makeClassifier(aFile: File, classifierType: ClassifierType): Classifier[Array[Double]] = {
    val store = new ShapefileDataStore(aFile.toJava.toURI.toURL)
    var i = 0
    val x = mutable.ArrayBuffer[Array[Double]]()
    val y = mutable.ArrayBuffer[Int]()
    try {
      val reader = store.getFeatureReader
      println(reader.getFeatureType)
      try
        Try {
          val featureReader = Iterator.continually(reader.next).takeWhile(_ => reader.hasNext)
          featureReader.foreach { feature =>
            val id = feature.getAttribute("IDPAR").toString
            val buildable = Option(feature.getAttribute("buildable")).getOrElse("")

            def toInt(s: String): Option[Int] = {
              try {
                Some(s.toInt)
              } catch {
                case _: Throwable => None
              }
            }

            toInt(buildable.toString) match {
              case Some(b) =>
                if (b == 0 || b == 1) {
                  x += attributes(feature)
                  y += b
                  i += 1
                }
              case None =>
            }
            //val values = test(roadRatio, railwayRatio, buildingRatio, elongation)
            //output << id + ", " + values.mkString(",")
          }
        }

      finally reader.close()
    } finally store.dispose()
    println("added " + i + " features")
    classifierType match {
      case dt: DecisionTree => cart(x.toArray,y.toArray,dt.maxNodes)
      case gbt: GradientBoostedTrees => gbm(x.toArray,y.toArray)
      case ab: AdaBoost => adaboost(x.toArray,y.toArray)
      case _ => gbm(x.toArray,y.toArray)
    }
  }
  def readAndWriteParcels(aFile: File, classifier: Classifier[Array[Double]], output: File) = {
    output.parent.createDirectories()
    output.delete(swallowIOExceptions = true)
    output << "id, class"
    val store = new ShapefileDataStore(aFile.toJava.toURI.toURL)
    var i = 0
    try {
      val reader = store.getFeatureReader
      println(reader.getFeatureType)
      try {
        Try {
          val featureReader = Iterator.continually(reader.next).takeWhile(_ => reader.hasNext)
          featureReader.foreach { feature =>
            val id = feature.getAttribute("IDPAR").toString
            output << id + ", " + classifier.predict(attributes(feature))
            i += 1
            if (i % 10000 == 0) {
              println(i)
            }
          }
        }
      }
      finally reader.close()
    } finally store.dispose()
    println("added " + i + " features")
  }
  val folderIn = File("/home/julien/devel/aggregator")
  val folderOut = folderIn / "output"

  val geometryFactory = new GeometryFactory
  val factory = new ShapefileDataStoreFactory
  val groundTruth = folderIn / "ground_truth.shp"
  println(Calendar.getInstance.getTime + " now with the real stuff with " + groundTruth)
  val learntClassifier = makeClassifier(groundTruth, GradientBoostedTrees())
  println(learntClassifier)
  val parcelFile = folderIn / "parcels_measures_idf.shp"
  val out = folderOut / "parcel_classes_gbm.csv"
  println(Calendar.getInstance.getTime + " now with the predictions of " + parcelFile + " to " + out)
  readAndWriteParcels(parcelFile, learntClassifier, out)
  println(Calendar.getInstance.getTime + " done")
}
