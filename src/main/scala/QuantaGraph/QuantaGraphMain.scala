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


  println("Run PageRank at year increments")
  val yearSet: Set[Int] = sg.V().value("year").toSet()
  val pageRankResultsMap: Map[Int, List[(String, Double)]] = Map()
  val yearKey = Key[Int]("year")
  for (year <- yearSet.toList.sorted) {
    println(year + " // " + sg.V().has(yearKey, P.lte(year)).values("year").toList.mkString(", "))

    /* TODO: Extract subgraph with vertices <= year */
    val stepLabel = StepLabel[Graph]("subGraph")
    val subGraph = sg.V().has(yearKey, P.lte(year)).outE().subgraph(stepLabel).cap(stepLabel).head
    println(subGraph.V().values("year").toList().sorted)
    println(subGraph)
    println("====")


  }

  println("Running PageRank...")
  /*
  TODO: Switch this to OLAP Query (i.e. PageRankVertexProgram)
  val pageRankResults = sg.compute().program(PageRankVertexProgram.build().create().submit().get()
  */
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
