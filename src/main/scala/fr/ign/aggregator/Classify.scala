package fr.ign.aggregator

import java.util.Calendar

import better.files.File
import com.vividsolutions.jts.geom.GeometryFactory
import org.geotools.data.{DataUtilities, FeatureWriter, Transaction}
import org.geotools.data.shapefile.{ShapefileDataStore, ShapefileDataStoreFactory}
import org.opengis.feature.simple.{SimpleFeature, SimpleFeatureType}
import smile.classification.{gbm, _}

import scala.collection.mutable
import scala.util.Try

import scala.collection.JavaConversions._

object Classify extends App {
  abstract class ClassifierType
  case class DecisionTree(maxNodes: Int = 100) extends ClassifierType
  case class GradientBoostedTrees() extends ClassifierType
  case class AdaBoost() extends ClassifierType
  case class RandomForest() extends ClassifierType

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
    println("File : " + aFile)
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
      case rf: RandomForest =>
        val classifier = randomForest(x.toArray,y.toArray)
        println(s"error = ${classifier.error}")
        println("importance")
        classifier.importance.foreach(println)
        classifier
      case _ => gbm(x.toArray,y.toArray)
    }
  }
  def readAndWriteParcelsAsCSV(aFile: File, classifier: Classifier[Array[Double]], output: File) = {
    output.parent.createDirectories()
    // if the output file already exists, delete it first
    output.delete(swallowIOExceptions = true)
    // CSV file header
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
      } finally reader.close()
    } finally store.dispose()
    println("added " + i + " features")
  }
  def readAndWriteParcels(aFile: File, classifier: Classifier[Array[Double]], writer: FeatureWriter[SimpleFeatureType, SimpleFeature]) = {
    val store = new ShapefileDataStore(aFile.toJava.toURI.toURL)
    var i = 0
    try {
      val reader = store.getFeatureReader
      println(reader.getFeatureType)
      try {
        Try {
          val featureReader = Iterator.continually(reader.next).takeWhile(_ => reader.hasNext)
          featureReader.foreach { feature =>
            val predictedClass = classifier.predict(attributes(feature))
            val simpleFeature = writer.next
            simpleFeature.setAttributes(feature.getAttributes.toArray:+predictedClass.asInstanceOf[AnyRef])
            writer.write()
            i += 1
            if (i % 10000 == 0) {
              println(i)
            }
          }
        }
      } finally reader.close()
    } finally store.dispose()
    println("added " + i + " features")
  }

  def apply(inputParcelFile: File, inputGroundTruthFile: File, outputParcelFile: File) {
    outputParcelFile.parent.createDirectories
    val geometryFactory = new GeometryFactory
    println(Calendar.getInstance.getTime + " now with the real stuff with " + inputGroundTruthFile)
    val learntClassifier = makeClassifier(inputGroundTruthFile, RandomForest())
    println(learntClassifier)
    println(Calendar.getInstance.getTime + " now with the predictions of " + inputParcelFile + " to " + outputParcelFile)

    val factory = new ShapefileDataStoreFactory
    val dataStore = factory.createDataStore(outputParcelFile.toJava.toURI.toURL)
    val featureTypeName = "Object"
    val specs = "geom:MultiPolygon:srid=2154,IDPAR:String,WIDTH:Double,HEIGHT:Double,ELONGATION:Double,roadArea:Double,roadRatio:Double,railArea:Double,railRatio:Double,buildArea:Double,buildRatio:Double,riverArea:Double,riverRatio:Double,class:String"

    val featureType = DataUtilities.createType(featureTypeName, specs)
    dataStore.createSchema(featureType)
    val typeName = dataStore.getTypeNames()(0)
    val writer = dataStore.getFeatureWriterAppend(typeName, Transaction.AUTO_COMMIT)
    System.setProperty("org.geotools.referencing.forceXY", "true")
    readAndWriteParcels(inputParcelFile, learntClassifier, writer)
    writer.close
    dataStore.dispose
    println(Calendar.getInstance.getTime + " done")
  }
  def help(): Unit = println(
    """
      |Usage : Classify inputParcelFile inputGroundTruthFile outputParcelFile
      |""".stripMargin)

  if (args.length < 3) { help(); sys.exit(1) }

  val inputParcelFile = File(args.head)
  val inputGroundTruthFile = File(args(1))
  val outputParcelFile = File(args(2))
  outputParcelFile.parent.createDirectories()

  Classify(inputParcelFile, inputGroundTruthFile, outputParcelFile)
}
