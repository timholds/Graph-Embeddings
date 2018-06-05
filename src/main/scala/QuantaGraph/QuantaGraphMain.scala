package QuantaGraph

import QuantaGraph.QuantaStream.Quanta
import cats.effect.IO
import fs2.Stream
import gremlin.scala.ScalaGraph

object Main extends App {

  val s: Stream[IO, Quanta] = QuantaStream.getQuantaStream.take(10)
  val g: ScalaGraph = BuildQuantaGraph.buildJanusGraph(s)

  println("Vertices:")
  println(g.V().toList())
  println("Edges:")
  println(g.E().toList)
  println("Done.")

}
