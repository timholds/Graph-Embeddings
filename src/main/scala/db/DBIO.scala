import io.jvm.uuid._

import fs2._
import cats.effect._
import cats.instances.list._
import cats.syntax.parallel._
import org.janusgraph.core.JanusGraphFactory
import org.janusgraph.graphdb.transaction.StandardJanusGraphTx
import org.apache.commons.configuration.BaseConfiguration
import gremlin.scala._
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.{
  out,
  timeLimit
}
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._

import scala.concurrent.ExecutionContext.Implicits.global._

import scala.concurrent._
import scala.concurrent.duration._

object HowToRun extends App {
  import util._
  val cfg = new BaseConfiguration()
  cfg.setProperty("storage.backend", "inmemory")
  implicit val jdb = JanusGraphFactory.open(cfg).asScala
  implicit val accum = new ConcurrentHashMap[String, Either[List[Vertex], Vertex]]
//  val Q1 = Quanta(1, Array("2", 3))
//  val Q2 = Quanta(2, Array(3))
//  val Q3 = Quanta(3, Array.empty[String])
//  val s: Stream[IO, Quanta] = Stream(Q1, Q2, Q3)

  /* TODO: Fix "Quanta" collision */
  import datastream.QuantaStream
  import datastream.QuantaStream.Quanta
  val s: Stream[IO, Quanta] = QuantaStream.getQuantaStream

  implicit class QuantaToDBQuanta(q: Quanta) {
    val dbQuanta = DBQuanta(q.title,
                            q.lang,
                            q.year,
                            q.`abstract`,
                            q.url,
                            q.fos,
                            q.id,
                            q.references.toArray)
  }

  val res = s.map(_.dbQuanta).through(DBIO.insertPipe(accum, jdb))
  res.compile.drain.unsafeRunSync()
  println("done")
  println(jdb.V().toList())
  println(jdb.E().toList)
  println(jdb.V().map(_.toCC[DBQuanta]).toList)

}

object util {
  case class DBQuanta(title: String,
                      lang: String,
                      year: Int,
                      `abstract`: String,
                      url: List[String],
                      fos: List[String],
                      id: String,
                      refs: Array[String])
  //  /**
//    * Retrieval operations (including get) generally do not block,
//    * so may overlap with update operations (including put and remove).
//    * Retrievals reflect the results of the most recently completed update
//    * operations holding upon their onset. (More formally, an update
//    * operation for a given key bears a happens-before relation with
//    * any (non-null) retrieval for that key reporting the updated value.)
//    */
  type SEEN = ConcurrentHashMap[String, Either[List[Vertex], Vertex]]
}

object DBIO {
  import util._

  def insert(node: DBQuanta, g: ScalaGraph)(
      implicit accum: SEEN): IO[Vertex] = {
    IO {
      val tx = g.tx.createThreadedTx().asInstanceOf[StandardJanusGraphTx]
      implicit val txg = tx.getGraph.asScala()
      //implicit val grph = g
      val res = tx + node
      accum.get(node.id) match {
        case Left(toAddEdges) =>
          toAddEdges.map(vrtx => tx.V(vrtx).head --- "cites" --> res)
          accum.put(node.id, Right(res))
        case Right(sameVertex) =>
          throw new IllegalStateException("this shouldn't happen")
        case null => accum.put(node.id, Right(res))
      }

      node.refs.map(reference =>
        accum.get(reference) match {
          case Left(toCiteStill) =>
            accum.put(reference, Left(res :: toCiteStill))
          case Right(vrtx) =>
            res --- "cites" --> tx.V(vrtx.id).head
          case null => accum.put(reference, Left(List(res)))
      })

      tx.tx().commit()
      g.tx().commit()
      res
    }
  }
  def insertPipe(state: SEEN, g: ScalaGraph): Pipe[IO, DBQuanta, Vertex] = {
    in: Stream[IO, DBQuanta] =>
      implicit val stateI = state

      in.flatMap(q => Stream.eval[IO, Vertex](insert(q, g)))
  }

}
