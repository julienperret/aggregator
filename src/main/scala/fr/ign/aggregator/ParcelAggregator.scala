package fr.ign.aggregator

import better.files.File
import fr.ign.aggregator.Aggregator.{aggregate, args}
import fr.ign.aggregator.Utils.toPolygon
import org.geotools.geometry.jts.JTS
import org.geotools.referencing.CRS
import org.opengis.feature.simple.SimpleFeature

object ParcelAggregator extends App {
  def help(): Unit = println(
    """
      |Usage : ParcelAggregator inputDir outputFile inputSRID groundTruth?
      |inputSRID defaults to 2154.
      |By default, groundTruth is false.
      |
      |Examples:
      | ParcelAggregator ./in ./out 2154
      | ParcelAggregator ./in ./out 4326 groundTruth
      |""".stripMargin)

  if (args.length < 2) { help(); sys.exit(1) }

  val dataDir = File(args.head)
  val outputFile = File(args(1))
  outputFile.parent.createDirectories()

  val inputSRID = if (args.length > 2) args(2) else "2154"

  val isGroundTruth = (args.length > 3) && args(3).equalsIgnoreCase("groundTruth")

  def apply(dataDir: File, outputFile: File, inputSRID: String, isGroundTruth: Boolean) {

    println(isGroundTruth)

    val childrenParcels = dataDir.collectChildren(f => f.name.endsWith("parcelles.shp"))

    val specsParcel = "geom:MultiPolygon:srid=2154,IDPAR:String" + (if (isGroundTruth) ",buildable:String" else "")

    val (inCRS, outCRS) = (CRS.decode(s"EPSG:$inputSRID"), CRS.decode("EPSG:2154"))
    val transform = CRS.findMathTransform(inCRS, outCRS, true)

    def valuesParcel(feature: SimpleFeature) = {
      if (isGroundTruth)
        Array[AnyRef](JTS.transform(toPolygon(feature.getDefaultGeometry), transform), feature.getAttribute("id").toString, feature.getAttribute("buildable").toString)
      else
        Array[AnyRef](JTS.transform(toPolygon(feature.getDefaultGeometry), transform), feature.getAttribute("id").toString)
    }

    aggregate(childrenParcels, outputFile, specsParcel, valuesParcel)
    println("parcels done")
  }
  ParcelAggregator(dataDir, outputFile, inputSRID, isGroundTruth)
}
