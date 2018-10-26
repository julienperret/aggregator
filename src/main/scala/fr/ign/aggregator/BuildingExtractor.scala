package fr.ign.aggregator

import java.io.{BufferedInputStream, File}
import java.net.URL
import java.nio.file.{Files, Paths}
import java.util.Calendar

import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.utils.IOUtils

object BuildingExtractor extends App {
  println(s"args.length =  ${args.length}")
  val date = args.head
  val ftp = args(1).equalsIgnoreCase("ftp")
  println(s"chose $date")
  def filter1(s: String) = s.contains("BATIMENT")
  val startDirString1 = "DONNEES/D0"
  val endDirString1 = "_"
  def filter2(s: String) = s.contains("BATI_")
  val startDirString2 = "_D0"
  val endDirString2 = "-ED"
  val login = sys.env("IGN_FTP_LOGIN")
  val password = sys.env("IGN_FTP_PASSWORD")
  val ftpBase = s"ftp://$login:$password@ftp2.ign.fr/BDTOPO_$date"
  val fileBase = s"BDTOPO/BDTOPO_date"
  val base = if (ftp) ftpBase else fileBase
  def fileFilter(s:String) = date match {
    case "2005" => filter1(s)
    case _ => filter2(s)
  }
  val (inputFile, outputDirectory, startDirString, endDirString) = date match {
    case "2005" => (s"$base/dpsg2017-06-00416.tar.gz", "BATI_BDTOPO_2005", startDirString1, endDirString1)
    case "2009" => (s"$base/dpsg2017-06-00418.tar.gz", "BATI_BDTOPO_2009", startDirString2, endDirString2)
    case "2010" => (s"$base/dpsg2017-06-00419.tar.gz", "BATI_BDTOPO_2010", startDirString2, endDirString2)
    case "2011" => (s"$base/dpsg2017-06-00420.tar.gz", "BATI_BDTOPO_2011", startDirString2, endDirString2)
    case "2012" => (s"$base/dpsg2017-06-00421.tar.gz", "BATI_BDTOPO_2012", startDirString2, endDirString2)
    case "2013" => (s"$base/dpsg2017-06-00422.tar.gz", "BATI_BDTOPO_2013", startDirString2, endDirString2)
    case "2014" => (s"$base/dpsg2017-06-00423.tar.gz", "BATI_BDTOPO_2014", startDirString2, endDirString2)
    case "2015" => (s"$base/dpsg2017-06-00424.tar.gz", "BATI_BDTOPO_2015", startDirString2, endDirString2)
    case "2016" => (s"$base/dpsg2017-06-00425.tar.gz", "BATI_BDTOPO_2016", startDirString2, endDirString2)
    case "2017" => (s"$base/dpsg2017-06-00417.tar.gz", "BATI_BDTOPO_2017", startDirString2, endDirString2)
    case _ => throw new IllegalArgumentException
  }

  def getInputStream(input: String) = {
    if (input.startsWith("ftp")) {
      val url = new URL(inputFile)
      val urlConnection = url.openConnection
      urlConnection.setConnectTimeout(1000)
      urlConnection.getInputStream
    } else {
      Files.newInputStream(Paths.get(inputFile))
    }
  }
  try {
    println(Calendar.getInstance.getTime + " let's go")
    val is = getInputStream(inputFile)
    val bi = new BufferedInputStream(is)
    val gzi = new GzipCompressorInputStream(bi)
    val o = new TarArchiveInputStream(gzi)
    try {
      var entry:ArchiveEntry = null
      var entries = 0
      var stop = false
      while (Option(entry = o.getNextEntry).isDefined && !stop) if (!o.canReadEntryData(entry)) {
        stop = true 
      } else {
        if (!entry.isDirectory && fileFilter(entry.getName)) {
          println(entry.getName)
          val start = entry.getName.indexOf(startDirString)
          val end = entry.getName.indexOf(endDirString)
          val startName = entry.getName.lastIndexOf("/")
          val dirName = entry.getName.substring(start + startDirString.length, end)
          val dir = new File(outputDirectory, dirName)
          dir.mkdirs()
          val file = new File(dir, entry.getName.substring(startName))
          val output = Files.newOutputStream(file.toPath)
          IOUtils.copy(o, output)
          entries = entries + 1
        } else {
          if (entry.isDirectory) println("\tignored:" + entry.getName)
        }
      }
      println(Calendar.getInstance.getTime + " I found " + entries + " files")
    } finally {
      if (is != null) is.close()
      if (bi != null) bi.close()
      if (gzi != null) gzi.close()
      if (o != null) o.close()
    }
    println(Calendar.getInstance.getTime + " I'm done")
  }
}
