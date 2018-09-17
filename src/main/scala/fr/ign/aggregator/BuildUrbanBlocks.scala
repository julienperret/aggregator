package fr.ign.aggregator

import java.util
import java.util.Calendar

import better.files.File
import com.vividsolutions.jts.geom.{Geometry, GeometryFactory, PrecisionModel}
import com.vividsolutions.jts.index.strtree.STRtree
import com.vividsolutions.jts.precision.GeometryPrecisionReducer
import fr.ign.aggregator.Utils._
import org.geotools.data.shapefile.ShapefileDataStoreFactory
import org.geotools.data.{DataStoreFinder, DataUtilities, Transaction}
import org.jgrapht.alg.ConnectivityInspector
import org.jgrapht.graph._


//Create parcel groups according to their adjacencies
object BuildUrbanBlocks extends App {
  class Edge extends DefaultEdge {
    override def getSource = super.getSource.asInstanceOf[String]
    override def getTarget = super.getTarget.asInstanceOf[String]
  }
  val gpr = new GeometryPrecisionReducer(new PrecisionModel(1000))
  val graph = new DefaultDirectedGraph[String,Edge](classOf[Edge])

  val folder = "/home/mbrasebin/Documents/Donnees/IAUIDF/Classification/ground_truth/out"
  //val folder = "/home/julien/devel/aggregator"
  val out = File(folder) / "output"

  val parcels = File(folder) / "parcels_idf.shp"
  println(Calendar.getInstance.getTime + " building index")
  //var processed = scala.collection.mutable.Set[String]()
  val map = new util.HashMap[String, Object]()
  map.put("url", parcels.toJava.toURI.toURL)
  val dataStoreIndex = DataStoreFinder.getDataStore(map)
  val typeNameIndex = dataStoreIndex.getTypeNames()(0)
  val sourceIndex = dataStoreIndex.getFeatureSource(typeNameIndex)
  val fs = sourceIndex.getFeatures.features()
  val index = new STRtree()
  var parcelMap = scala.collection.mutable.HashMap[String, Parcel]()
  println(Calendar.getInstance.getTime + " spatial index")
  while (fs.hasNext) {
    val feature = fs.next()
    val geom = toPolygon(gpr.reduce(feature.getDefaultGeometry.asInstanceOf[Geometry]))
    val idpar = feature.getAttribute("IDPAR").toString
    val parcel = new Parcel(geom, idpar)
    index.insert(geom.getEnvelopeInternal, parcel)
    parcelMap.put(idpar, parcel)
  }
  dataStoreIndex.dispose()
  println(Calendar.getInstance.getTime + " spatial index collection built")
  def process(o: Parcel) = {
    val env = o.geom.getEnvelopeInternal
    val buffer = o.geom.buffer(0.1)
    def connected(a: Parcel) = a.geom.intersects(buffer)
    val collection = index.query(env)
    val touches = collection.toArray.map(_.asInstanceOf[Parcel]).filter(_.id > o.id).filter(connected)
    if (!graph.containsVertex(o.id)) graph.addVertex(o.id)
    touches.foreach{d=>
      if (!graph.containsVertex(d.id)) graph.addVertex(d.id)
      graph.addEdge(o.id, d.id)
    }
  }
  var i: Int = 0
  for (feature <- parcelMap) {
    process(feature._2)
    i += 1
    if (i % 10000 == 0) println(Calendar.getInstance.getTime + " " + i)
  }
  println(Calendar.getInstance.getTime + " processing done")
  val undirectedGraph = new AsUndirectedGraph(graph)
  def writeGraph(aGraph: AsUndirectedGraph[String, Edge], aFile: File) = {
    val specs = "geom:LineString:srid=2154,gid:Integer,id1:String,id2:String"
    val factory = new ShapefileDataStoreFactory
    println("creating file " + aFile)
    aFile.parent.createDirectories()
    val dataStore = factory.createDataStore(aFile.toJava.toURI.toURL)
    val featureTypeName = "Object"
    val featureType = DataUtilities.createType(featureTypeName, specs)
    dataStore.createSchema(featureType)
    val typeName = dataStore.getTypeNames()(0)
    val writer = dataStore.getFeatureWriterAppend(typeName, Transaction.AUTO_COMMIT)
    System.setProperty("org.geotools.referencing.forceXY","true")
    println(Calendar.getInstance.getTime + " now with the real stuff")
    var id: Int = 0
    val geomFactory = new GeometryFactory()
    aGraph.edgeSet().forEach{edge=>
      val source = edge.getSource
      val target = edge.getTarget
      val sourceCoord = parcelMap(source).geom.getCentroid.getCoordinate
      val targetCoord = parcelMap(target).geom.getCentroid.getCoordinate
      val f = writer.next()
      f.setAttributes(Array[AnyRef](geomFactory.createLineString(Array(sourceCoord, targetCoord)),id.asInstanceOf[Integer], source, target))
      writer.write()
      id += 1
    }
    println(Calendar.getInstance.getTime + " done")
    writer.close()
    dataStore.dispose()
  }
  writeGraph(undirectedGraph, out / "graph.shp")
  val connectivityInspector = new ConnectivityInspector(undirectedGraph)
  println(Calendar.getInstance.getTime + " connected sets")
  val connectedSets = connectivityInspector.connectedSets()
  var j: Int = 0
  println(Calendar.getInstance.getTime + " connected sets export " + connectedSets.size())
  val specs = "geom:MultiPolygon:srid=2154,IDPAR:String,idBlock:Integer"
  val factory = new ShapefileDataStoreFactory
  val file = out / "parcels_connected.shp"
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
  var connectedSetMap = scala.collection.mutable.HashMap[String, Int]()
  connectedSets.forEach{s=>
    s.forEach{v=>connectedSetMap.put(v,j)}
    j += 1
  }
  println(Calendar.getInstance.getTime + " write")
  for (feature <- parcelMap) {
    if (connectedSetMap.contains(feature._1)) {
      val component = connectedSetMap(feature._1)
      val f = writer.next()
      f.setAttributes(Array[AnyRef](feature._2.geom, feature._1, component.asInstanceOf[Integer]))
      writer.write()
    }
  }
  println(Calendar.getInstance.getTime + " done")
  writer.close()
  dataStore.dispose()
}
