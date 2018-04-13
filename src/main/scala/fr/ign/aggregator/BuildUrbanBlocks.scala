package fr.ign.aggregator

import java.util
import java.util.Calendar

import better.files.File
import com.vividsolutions.jts.geom.{Geometry, PrecisionModel}
import com.vividsolutions.jts.index.strtree.STRtree
import com.vividsolutions.jts.precision.GeometryPrecisionReducer
import fr.ign.aggregator.Utils._
import org.geotools.data.collection.SpatialIndexFeatureCollection
import org.geotools.data.shapefile.ShapefileDataStoreFactory
import org.geotools.data.{DataStoreFinder, DataUtilities, Transaction}
import org.geotools.factory.CommonFactoryFinder
import org.geotools.geometry.jts.ReferencedEnvelope
import org.jgrapht.alg.ConnectivityInspector
import org.jgrapht.graph._
import org.opengis.feature.simple.SimpleFeature

object BuildUrbanBlocks extends App {
  val gpr = new GeometryPrecisionReducer(new PrecisionModel(1000))
  val graph = new DefaultDirectedGraph[String,DefaultEdge](classOf[DefaultEdge])
  val folder = "/home/julien/devel/aggregator"
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
  println(Calendar.getInstance.getTime + " reduce")
  while (fs.hasNext) {
    val current = fs.next()
    val geom = toPolygon(gpr.reduce(current.getDefaultGeometry.asInstanceOf[Geometry]))
    current.setDefaultGeometry(geom)
  }
  val spatialIndexCollection = new SpatialIndexFeatureCollection(sourceIndex.getSchema)
  println(Calendar.getInstance.getTime + " spatial index collection start")
  spatialIndexCollection.addAll(sourceIndex.getFeatures)
  println(Calendar.getInstance.getTime + " spatial index collection built")
  // Fast spatial Access
  val sourceSpatial = DataUtilities.source(spatialIndexCollection)
  println(Calendar.getInstance.getTime + " spatial index source built")
  val geometryPropertyName = sourceSpatial.getSchema.getGeometryDescriptor.getLocalName
  val ff = CommonFactoryFinder.getFilterFactory2
  val crs = sourceSpatial.getSchema.getCoordinateReferenceSystem
  def process(o: Parcel) = {
    val env = o.geom.getEnvelopeInternal
    val buffer = o.geom.buffer(0.1)
    def connected(a: Parcel) = a.geom.intersects(buffer)
    val bbox = new ReferencedEnvelope(env.getMinX, env.getMinY, env.getMaxX, env.getMaxY, crs)
    val filter = ff.bbox(ff.property(geometryPropertyName), bbox)
    val collection = sourceSpatial.getFeatures(filter)
    /*filterNot(d=>processed.contains(d.id)).*/
    val touches = collection.toArray.map(_.asInstanceOf[SimpleFeature]).map(f=>new Parcel(toPolygon(f.getDefaultGeometry),f.getAttribute("IDPAR").toString)).filter(_.id > o.id).filter(connected)
    if (!graph.containsVertex(o.id)) graph.addVertex(o.id)
    touches.foreach{d=>
      if (!graph.containsVertex(d.id)) graph.addVertex(d.id)
      graph.addEdge(o.id, d.id)
    }
    //processed += o.id
  }
  val features = spatialIndexCollection.features()
  var i: Int = 0
  while (features.hasNext) {
    val feature = features.next
    val parcel = new Parcel(toPolygon(feature.getDefaultGeometry), feature.getAttribute("IDPAR").toString)
    process(parcel)
    i += 1
    if (i % 10000 == 0) println(Calendar.getInstance.getTime + " " + i)
  }
  dataStoreIndex.dispose()
  println(Calendar.getInstance.getTime + " processing done")
  val undirectedGraph = new AsUndirectedGraph(graph)
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
  val featuresFinal = sourceSpatial.getFeatures.features()
  println(Calendar.getInstance.getTime + " write")
  while (featuresFinal.hasNext) {
    val ff = featuresFinal.next
    val id = ff.getAttribute("IDPAR").toString
    if (connectedSetMap.contains(id)) {
      val component = connectedSetMap(id)
      val f = writer.next()
      f.setAttributes(Array[AnyRef](ff.getDefaultGeometry, id, component.asInstanceOf[Integer]))
      writer.write()
    }
  }
  println(Calendar.getInstance.getTime + " done")
  writer.close()
  dataStore.dispose()
}
