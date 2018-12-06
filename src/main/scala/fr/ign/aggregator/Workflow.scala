package fr.ign.aggregator

import better.files.File

object Workflow extends App {
  // BDTOPO dir
  val dataDir = File("")
  // parcel dir
  val parcelDir = File("")
  // output data dir
  val outputDir = File("")
  // output classification report?
  val jsonReport = File("")
  // output parcel file
  val outputParcel = File("")


  // Workflow :
  // inputs = bdtopo dir + parcel dir / url ? + groundtruth file + classifier type + (temp data dir) ?
  // output = parcel file + ( error + importance )? + directory with computed data ?

  //1: Aggregator = extract relevant files (BDTOPO + Parcelles) and aggregate them & build surface files from linear ones
  // input: bdtopo dir + parcel dir + output dir + booleans + bool (ground truth)
  // output: a directory (output dir) with the appropriate files
//  Aggregator(dataDir, parcelDir, outputDir, true, true, true, true, true, true, true)

//  ParcelAggregator(dataDir, outputFile, 2154, false)
//  ParcelAggregator(dataDir, outputFile, 2154, true)

  //2: ComputeMeasures = compute measures on parcels
  // input: directory with appropriate files (parcels, roads, etc.) + output file + bool (ground truth)
  // output: a shapefile
// ComputeMeasures(inputDir, inputParcelFile, outputParcelFile, false)
// ComputeMeasures(inputDir, inputParcelFile, outputParcelFile, true)

  //3: Classify = classify parcels using ground truth
  // input: parcel file + ground truth file + output file + classifier type
  // output: classified parcel file + ( error + importance )?
}
