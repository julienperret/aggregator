package fr.ign.aggregator

import java.util.Calendar

import better.files.File
import fr.ign.aggregator.ShapefileAggregator.Parameters

object Workflow extends App {
  def apply(inputDataDir: File, inputParcelDir: File, inputParcelGroundTruthDir: File, outputDir: File, outputParcelFile: File): Unit = {
//    val jsonReport = File("")

    // Workflow :
    // inputs = bdtopo dir + parcel dir / url ? + groundtruth file + classifier type + (temp data dir) ?
    // output = parcel file + ( error + importance )? + directory with computed data ?

    //1: Aggregator = extract relevant files (BDTOPO + Parcelles) and aggregate them & build surface files from linear ones
    // input: bdtopo dir + parcel dir + output dir + booleans + bool (ground truth)
    // output: a directory (output dir) with the appropriate files
    println("Aggregate data: " + Calendar.getInstance().getTime)
    val dataAggregateDir = outputDir / "data"
    ShapefileAggregator(inputDataDir, dataAggregateDir, Parameters())

    println("Aggregate parcel data: " + Calendar.getInstance().getTime)
    val parcelAggregateFile = outputDir / "parcels" / "parcels.shp"
    ParcelAggregator(inputParcelDir, parcelAggregateFile, "2154", false)

    println("Aggregate ground truth data: " + Calendar.getInstance().getTime)
    val parcelAggregateGroundTruthFile = outputDir / "parcels" / "parcels_ground_truth.shp"
    ParcelAggregator(inputParcelGroundTruthDir, parcelAggregateGroundTruthFile, "2154", true)

    //2: ComputeMeasures = compute measures on parcels
    // input: directory with appropriate files (parcels, roads, etc.) + output file + bool (ground truth)
    // output: a shapefile
    println("Compute measures: " + Calendar.getInstance().getTime)
    val parcelMeasureFile = outputDir / "parcels_measures" / "parcels.shp"
    ComputeMeasures(dataAggregateDir, parcelAggregateFile, parcelMeasureFile, false)

    println("Compute ground truth measures: " + Calendar.getInstance().getTime)
    val parcelMeasureGroundTruthFile = outputDir / "parcels_measures" / "parcels_ground_truth.shp"
    ComputeMeasures(dataAggregateDir, parcelAggregateGroundTruthFile, parcelMeasureGroundTruthFile, true)

    //3: Classify = classify parcels using ground truth
    // input: parcel file + ground truth file + output file + classifier type
    // output: classified parcel file + ( error + importance )?
    println("Classify: " + Calendar.getInstance().getTime)
    Classify(parcelMeasureFile, parcelMeasureGroundTruthFile, outputParcelFile)
    println("Done: " + Calendar.getInstance().getTime)
  }
  def help(): Unit = println(
  """
    |Usage : Workflow inputDataDir inputParcelDir parcelGroundTruthDir outputDir outputParcelFile
    |
    |outputDir is a temporary directory to put all the computed data.
    |
    |Example:
    | Workflow ./data/BDT_2-2_SHP_LAMB93_D094-ED171 ./cadastre/cadastre-94-parcelles-shp ./truth ./temp_workflow ./output_workflow/parcels.shp
    |""".stripMargin)

  if (args.length < 5) { help(); sys.exit(1) }

  val dataDir = File(args.head)
  val parcelDir = File(args(1))
  val parcelGroundTruthDir = File(args(2))
  val outputDir = File(args(3))
  val outputParcel = File(args(4))
  Workflow(dataDir, parcelDir, parcelGroundTruthDir, outputDir, outputParcel)
}
