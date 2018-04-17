package fr.ign.aggregator

import better.files.File
import com.vividsolutions.jts.geom
import com.vividsolutions.jts.geom.util.AffineTransformation
import com.vividsolutions.jts.geom._
import com.vividsolutions.jts.index.strtree.STRtree
import org.geotools.data.shapefile.ShapefileDataStore
import org.opengis.feature.simple.SimpleFeature

import scala.util.Try

object Utils {
  def toPolygon(a: Any) = {
    val g = a.asInstanceOf[Geometry]
    g match {
      case mp: MultiPolygon => mp
      case _ => g.getFactory.createMultiPolygon(Array(g.asInstanceOf[Polygon]))
    }
  }
  def indexPolygon(aFile: File, filter: SimpleFeature=>Boolean = _=>true) = {
    val store = new ShapefileDataStore(aFile.toJava.toURI.toURL)
    val index = new STRtree()
    try {
      val reader = store.getFeatureReader
      try {
        Try {
          val featureReader = Iterator.continually(reader.next).takeWhile(_ => reader.hasNext)
          featureReader.foreach { feature =>
            if (filter(feature)) {
              val geom = toPolygon(feature.getDefaultGeometry)
              index.insert(geom.getEnvelopeInternal, geom)
            }
          }
        }
      } finally reader.close()
    } finally store.dispose()
    index
  }
  class Parcel(val geom: MultiPolygon, val id: String)

  def indexPolygon(aFile: File, attribute: String) = {
    val store = new ShapefileDataStore(aFile.toJava.toURI.toURL)
    val index = new STRtree()
    var i: Int = 0
    try {
      val reader = store.getFeatureReader
      try {
        Try {
          val featureReader = Iterator.continually(reader.next).takeWhile(_ => reader.hasNext)
          featureReader.foreach { feature =>
            val geom = toPolygon(feature.getDefaultGeometry)
            index.insert(geom.getEnvelopeInternal, new Parcel(geom, feature.getAttribute(attribute).toString))
            i += 1
            if (i % 10000 == 0) println(i)
          }
        }
      } finally reader.close()
    } finally store.dispose()
    index
  }

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
  def envelopes(bounds: Envelope, size: Double) = {
    val n = (bounds.getWidth / size).toInt + 1
    val m = (bounds.getHeight / size).toInt + 1
    println("grid " + n + " x " + m)
    val minX = bounds.getMinX
    val minY = bounds.getMinY
    for {
      i <- 0 until n
      j <- 0 until m
    } yield new geom.Envelope(minX + i * size, minX + (i + 1) * size, minY + j * size, minY + (j + 1) * size)
  }
}
