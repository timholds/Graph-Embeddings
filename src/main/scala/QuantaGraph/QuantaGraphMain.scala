package QuantaGraph

import java.util
import java.util.{ArrayList => JArrayList}

import cats.effect.IO
import datastream._
import fs2.Stream
import gremlin.scala._
import org.apache.tinkerpop.gremlin.process.traversal.Order

import scala.collection.JavaConverters._


object Main extends App {

  println("JVM Heap\n\ttotalMemory: " + Runtime.getRuntime.totalMemory / 1e9d +
    " Gb\n\tmaxMemory: " + Runtime.getRuntime.maxMemory / 1e9d + " Gb")

  println("Building graph...")
  val s: Stream[IO, Quanta] = new QuantaStream[IO].getQuantaStream
  val sg: ScalaGraph = BuildQuantaGraph.buildJanusGraph(s)
  println("Build graph with: \n\t" + sg.V.count.head + " Vertices\n\t" + sg.E.count.head + " Edges")

  sg.V().valueMap().toList.foreach(v => println(v.get("publisher")))

  println("Running PageRank...")
  val numResultsToReturn = 10000
  val pageRankResults = sg.traversalSource.underlying.withComputer().V()
    .pageRank().by("pageRank")
    .order().by("pageRank", Order.decr)
    .limit(numResultsToReturn)
    .valueMap("title", "pageRank")
    .toList
    .asScala


  val outputFilename = "pageRankResults.txt"
  println("Printing results to " + outputFilename + "...")

  def node2String(v: util.Map[String, Nothing]) = v.get("title").asInstanceOf[JArrayList[String]].get(0) + "," +
    v.get("pageRank").asInstanceOf[JArrayList[Double]].get(0) + "\r\n"

  import java.io._

  val bw = new BufferedWriter(new FileWriter(new File(outputFilename)))
  pageRankResults
    .map(v => node2String(v))
    .foreach(v => bw.write(v))
  bw.close()

  println("Done.")
}
