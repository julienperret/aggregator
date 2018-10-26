package fr.ign.aggregator

import better.files.File
import com.vividsolutions.jts.geom.GeometryFactory
import org.geotools.data.shapefile.{ShapefileDataStore, ShapefileDataStoreFactory}
import java.util
import java.util.Calendar

import scala.util.Try

object Classify extends App {
//  def test(roadRatio:Double,railRatio:Double,buildRatio:Double,elongation:Double)={
//    def configSet[T](d:VariableSet[T], s: T) = {
//      val l = new util.ArrayList[T]
//      l.add(s)
//      val c = new Configuration[T](d, l)
//      val cs = new ConfigurationSet[T](d)
//      cs.add(c)
//      cs
//    }
//    val vf = new VariableFactory[String]
//    val road = "ROAD"
//    val rail = "RAIL"
//    val build = "BUILD"
//    val empty = "EMPTY"
//    val variable = vf.newVariable
//    variable.add(road)
//    variable.add(rail)
//    variable.add(build)
//    variable.add(empty)
//    //println("Variable: " + variable)
//    val variableSet = new VariableSet[String](vf)
//    variableSet.add(variable)
//    //println("nb of configurations = " + variableSet.getNumberOfConfigurations)
//    val roadCS = configSet(variableSet,road)
//    val railCS = configSet(variableSet,rail)
//    val buildCS = configSet(variableSet,build)
//    val emptyCS = configSet(variableSet,empty)
//    val ignoranceCS = new ConfigurationSet[String](variableSet)
//    ignoranceCS.addAllConfigurations()
//    val ignorance = 0.01
//    val certitude = 1.0 - ignorance
//    // first source
//    val mp1 = new MassPotential[String](variableSet)
//    val r = Math.max(0.0,roadRatio * certitude - ignorance)
//    mp1.add(roadCS, r)
//    mp1.add(railCS, 0.0)
//    mp1.add(buildCS, 0.0)
//    mp1.add(emptyCS,ignorance)
//    mp1.add(ignoranceCS, certitude-r)
//    mp1.check()
//    //println("Source 1:\n" + mp1)
//    // second source
//    val mp2 = new MassPotential[String](variableSet)
//    val rr = Math.max(0.0,railRatio * certitude - ignorance)
//    mp2.add(roadCS, 0.0)
//    mp2.add(railCS, rr)
//    mp2.add(buildCS, 0.0)
//    mp2.add(emptyCS, ignorance)
//    mp2.add(ignoranceCS, certitude-rr)
//    mp2.check()
//    //println("Source 2:\n" + mp2)
//    // third source
//    val mp3 = new MassPotential[String](variableSet)
//    val b = Math.max(0.0,buildRatio * certitude - ignorance)
//    mp3.add(roadCS, 0.0)
//    mp3.add(railCS, 0.0)
//    mp3.add(buildCS, b)
//    mp3.add(emptyCS,ignorance)
//    mp3.add(ignoranceCS, certitude-b)
//    mp3.check()
//    //println("Source 3:\n" + mp3)
//    // fourth source
//    val mp4 = new MassPotential[String](variableSet)
//    val e = Math.max(0.0,(1.0 - elongation) * certitude - ignorance)
//    mp4.add(roadCS, e)
//    mp4.add(railCS, 0.0)
//    mp4.add(buildCS, 0.0)
//    mp4.add(emptyCS,ignorance)
//    mp4.add(ignoranceCS, certitude-e)
//    mp4.check()
//    // combination
//    // Dempster
//    def res(massPotential: MassPotential[String]) = {
//      val roadMassD = massPotential.mass(roadCS)
//      val pigroadMassD = massPotential.pignistic(roadCS)
//      val railMassD = massPotential.mass(railCS)
//      val pigrailMassD = massPotential.pignistic(railCS)
//      val buildMassD = massPotential.mass(buildCS)
//      val pigbuildMassD = massPotential.pignistic(buildCS)
//      val emptyMassD = massPotential.mass(emptyCS)
//      val pigemptyMassD = massPotential.pignistic(emptyCS)
//
//      //println(s"road ${roadMassD} pig = ${pigroadMassD}")
//      //println(s"rail ${railMassD} pig = ${pigrailMassD}")
//      //println(s"build ${buildMassD} pig = ${pigbuildMassD}")
//      //println(s"empty ${emptyMassD} pig = ${pigemptyMassD}")
//
//      val decision = massPotential.decide(true).toString
//      val decisionValue = decision.substring(2,decision.length-2)
//
//      Seq(roadMassD, pigroadMassD, railMassD, pigrailMassD, buildMassD, pigbuildMassD, emptyMassD, pigemptyMassD, decisionValue)
//    }
//    val dempsterPotential = mp1.combination(mp2).combination(mp3).norm//combination(mp4).norm
//    //System.out.println("DEMPSTER MP 1 + MP 2 + MP 3:\n" + dempsterPotential)
//    val dp = res(dempsterPotential)
//    //val cumulativePotential = mp1.cumulativeRule(mp2, false).cumulativeRule(mp3, false).cumulativeRule(mp4, false).norm()
//    //System.out.println("CUMULATIVE MP 1 + MP 2 + MP 3:\n" + cumulativePotential)
//    //val cp = res(cumulativePotential)
//    dp// ++ cp
//  }
//  def readAndWriteParcels(aFile: File, output: File) = {
//    output.parent.createDirectories()
//    output.delete(swallowIOExceptions = true)
//    output << "id, rmD, rpD, amD, apD, bmD, bpD, emD, epD, decisionD"//, rmC, rpC, amC, apC, bmC, bpC, emC, epC, decisionC"
//    val store = new ShapefileDataStore(aFile.toJava.toURI.toURL)
//    var i = 0
//    try {
//      val reader = store.getFeatureReader
//      try
//        Try {
//          val featureReader = Iterator.continually(reader.next).takeWhile(_ => reader.hasNext)
//          featureReader.foreach { feature =>
//            val id = feature.getAttribute("IDPAR").toString
//            val roadRatio = feature.getAttribute("roadRatio").asInstanceOf[Double]
//            val railwayRatio = feature.getAttribute("railRatio").asInstanceOf[Double]
//            val buildingRatio = feature.getAttribute("buildRatio").asInstanceOf[Double]
//            val elongation = feature.getAttribute("ELONGATION").asInstanceOf[Double]
//            println(id)
//            val values = test(roadRatio, railwayRatio, buildingRatio, elongation)
//            output << id + ", " + values.mkString(",")
//            i += 1
//          }
//        }
//      finally reader.close()
//    } finally store.dispose()
//    println("added " + i + " features")
//  }
//  val folder = "/home/mbrasebin/Bureau/Data_Fin/"
//
//  val geometryFactory = new GeometryFactory
//  val factory = new ShapefileDataStoreFactory
//  val parcelFile = File(folder + "parcels_measures_idf_2.shp")
//  val out = File(folder + "parcel_classes_dempster.csv")
//  println(Calendar.getInstance.getTime + " now with the real stuff")
//  readAndWriteParcels(parcelFile,out)
//  println(Calendar.getInstance.getTime + " done")
}
