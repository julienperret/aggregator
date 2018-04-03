package fr.ign.aggregator

import com.vividsolutions.jts.geom.{Geometry, MultiPolygon, Polygon}

object Utils {
  def toPolygon(a: Any) = {
    val g = a.asInstanceOf[Geometry]
    g match {
      case mp: MultiPolygon => mp
      case _ => g.getFactory.createMultiPolygon(Array(g.asInstanceOf[Polygon]))
    }
  }
}
