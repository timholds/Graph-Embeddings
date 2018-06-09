package QuantaGraph

import cats.effect.IO
import datastream._
import fs2.Stream
import gremlin.scala._
import org.apache.tinkerpop.gremlin.process.traversal.Order

object Main extends App {

  println("JVM Heap\n\ttotalMemory: " + Runtime.getRuntime.totalMemory / 1e9d +
    " Gb\n\tmaxMemory: " + Runtime.getRuntime.maxMemory / 1e9d + " Gb")

  println("Building graph...")
  val s: Stream[IO, Quanta] = new QuantaStream[IO].getQuantaStream
  val sg: ScalaGraph = BuildQuantaGraph.buildJanusGraph(s)
  println("Build graph with: \n\t" + sg.V.count.head + " Vertices\n\t" + sg.E.count.head + " Edges")

  println("Running PageRank...")
  val pageRankResults = sg.traversalSource.underlying.withComputer().V()
    .pageRank().by("pageRank")
    .order().by("pageRank", Order.decr).limit(20)
    .valueMap("pageRank", "title", "year")
    .toList.forEach(v => println(v.values()))

  println("Done.")
}
