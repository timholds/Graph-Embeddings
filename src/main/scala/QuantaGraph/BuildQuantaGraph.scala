package QuantaGraph

import java.util.concurrent.ConcurrentHashMap

import QuantaGraph.QuantaStream.Quanta
import cats.effect._
import fs2._
import gremlin.scala._
import org.apache.commons.configuration.BaseConfiguration
import org.janusgraph.core.JanusGraphFactory
import org.janusgraph.graphdb.transaction.StandardJanusGraphTx

import scala.concurrent.{Await, Promise, duration}
import scala.language.implicitConversions

object BuildQuantaGraph {

  case class DBQuanta(title: Option[String],
                      lang: Option[String],
                      year: Option[Int],
                      `abstract`: Option[String],
                      url: Option[Array[String]],
                      fos: Option[Array[String]],
                      id: String,
                      refs: Option[Array[String]])

  //  /**
  //    * Retrieval operations (including get) generally do not block,
  //    * so may overlap with update operations (including put and remove).
  //    * Retrievals reflect the results of the most recently completed update
  //    * operations holding upon their onset. (More formally, an update
  //    * operation for a given key bears a happens-before relation with
  //    * any (non-null) retrieval for that key reporting the updated value.)
  //    */
  type SEEN = ConcurrentHashMap[String, Either[List[Vertex], Vertex]]

  def buildJanusGraph(s: Stream[IO, Quanta]): ScalaGraph = {

    val cfg: BaseConfiguration = new BaseConfiguration()
    cfg.setProperty("storage.backend", "inmemory")
    //    val graph = JanusGraphFactory.open(cfg)
    val jdb: ScalaGraph = JanusGraphFactory.open(cfg).asScala
    val accum: ConcurrentHashMap[String, Either[List[Vertex], Vertex]] =
      new ConcurrentHashMap[String, Either[List[Vertex], Vertex]]
    import QuantaStream.Quanta

    implicit class QuantaToDBQuanta(q: Quanta) {

      implicit def safeList2Array(l: Option[List[String]]): Option[Array[String]] = {
        Option(l.getOrElse(List.empty).toArray)
      }

      val dbQuanta = DBQuanta(q.title, q.lang, q.year, q.`abstract`, q.url,
        q.fos, q.id, q.references)
    }

    val res = s.map(_.dbQuanta).through(insertPipe(accum, jdb))
    res.compile.drain.unsafeRunSync()
    jdb

  }

  def insert(node: DBQuanta, g: ScalaGraph)(implicit accum: SEEN): IO[Vertex] = {
    IO {
      val tx = g.tx.createThreadedTx().asInstanceOf[StandardJanusGraphTx]
      implicit val txg = tx.getGraph.asScala()
      val pres = Promise[Vertex]
      accum.get(node.id) match {
        case Left(toAddEdges) =>
          val nodeInG = tx + node
          toAddEdges.map(vrtx => tx.V(vrtx).head --- "cites" --> nodeInG)
          accum.put(node.id, Right(nodeInG))
          pres.success(nodeInG)
        case Right(vid) =>
          val rolledFwd = tx.V(vid).head
          pres.success(rolledFwd)
        case null =>
          val nodeInG = tx + node
          accum.put(node.id, Right(nodeInG))
          pres.success(nodeInG)
      }

      val res = Await.result(pres.future, duration.Duration.Inf)
      node.refs.map(_.map(reference =>
        accum.get(reference) match {
          case Left(toCiteStill) =>
            accum.put(reference, Left(res :: toCiteStill))
          case Right(vrtx) =>
            val rolledFwd = tx.V(vrtx.id).head()
            res.out("cites").hasId(rolledFwd).headOption() match {
              case Some(_) => None
              case None => res --- "cites" --> rolledFwd
            }
          case null => accum.put(reference, Left(List(res)))
        }))

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
