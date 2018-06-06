package QuantaGraph

import QuantaGraph.QuantaStream.Quanta
import cats.effect.IO
import fs2.Stream
import gremlin.scala.ScalaGraph
import org.apache.tinkerpop.gremlin.process.traversal.Order

object Main extends App {

  val s: Stream[IO, Quanta] = QuantaStream.getQuantaStream.take(10)
  val g: ScalaGraph = BuildQuantaGraph.buildJanusGraph(s)

  println("Vertices:")
  println(g.V().toList())
  println("Edges:")
  println(g.E().toList)

  println("PageRank:")
  val r = g.traversalSource.underlying.withComputer().V()
    .pageRank().by("pageRank")
    .order().by("pageRank", Order.decr)
    .valueMap("pageRank", "title", "year").toList

  r.forEach(v =>
    println(v.values())
  )

  println("Done.")
}
