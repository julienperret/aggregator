package fr.ign.aggregator

import java.io.{BufferedInputStream, File}
import java.net.URL
import java.nio.file.{Files, Paths}
import java.util.Calendar

import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveInputStream}
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.utils.IOUtils

object BuildingExtractor extends App {
  def filter1(s: String) = s.contains("BATIMENT")
  val startDirString1 = "DONNEES/D0"
  val endDirString1 = "_"
//  val (inputFile, outputDirectory, startDirString, endDirString) = ("BDTOPO/BDTOPO_2005/dpsg2017-06-00416.tar.gz", "BATI_BDTOPO_2005", startDirString1, endDirString1)
  def filter2(s: String) = s.contains("BATI_")
  val startDirString2 = "_D0"
  val endDirString2 = "-ED"
  val login = sys.env("IGN_FTP_LOGIN")
  val password = sys.env("IGN_FTP_PASSWORD")
  val (inputFile, outputDirectory, startDirString, endDirString) = (s"ftp://$login:$password@ftp2.ign.fr/BDTOPO_2009/dpsg2017-06-00418.tar.gz", "BATI_BDTOPO_2009", startDirString2, endDirString2)
//  val (inputFile, outputDirectory, startDirString, endDirString) = ("BDTOPO/BDTOPO_2017/dpsg2017-06-00417.tar.gz", "BATI_BDTOPO_2017", startDirString2, endDirString2)
  def fileFilter(s:String) = filter2(s)

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
      while (((entry = o.getNextEntry()) != null) && !stop) if (!o.canReadEntryData(entry)) {
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
