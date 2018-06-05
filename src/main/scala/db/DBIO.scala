import java.util.concurrent.ConcurrentHashMap

import cats.effect._
import datastream.QuantaStream
import datastream.Quanta
import fs2.StreamApp.ExitCode
import fs2._
import gremlin.scala._
import org.apache.commons.configuration.BaseConfiguration
import org.janusgraph.core.JanusGraphFactory
import org.janusgraph.graphdb.transaction.StandardJanusGraphTx

object HowToRun extends App {
  import util._
  val cfg = new BaseConfiguration()
  cfg.setProperty("storage.backend", "inmemory")
  implicit val jdb = JanusGraphFactory.open(cfg).asScala
  implicit val accum =
    new ConcurrentHashMap[String, Either[List[Vertex], Vertex]]

  import datastream.QuantaStream
  import datastream.QuantaStream.Quanta

  val s: Stream[IO, Quanta] = QuantaStream.getQuantaStream.take(5)

  implicit class QuantaToDBQuanta(q: Quanta) {
    val dbQuanta = DBQuanta(q.title,
                            q.lang,
                            q.year,
                            q.`abstract`,
                            q.url.map(_.toArray),
                            q.fos.map(_.toArray),
                            q.id,
      q.references.getOrElse(List.empty).toArray)
  }

  val res = s.map(_.dbQuanta).through(DBIO.insertPipe(accum, jdb))
  res.compile.drain.unsafeRunSync()
  println("done")
  println("# nodes :" + jdb.V().toList().length)
  println("# edges :" + jdb.E().toList.length)
  jdb.V().toList().map(_.toCC[DBQuanta])
}

object util {
  case class DBQuanta(title: Option[String],
                      lang: Option[String],
                      year: Option[Int],
                      `abstract`: Option[String],
                      url: Option[Array[String]],
                      fos: Option[Array[String]],
                      id: String,
                      refs: Array[String])
  implicit class QuantaToDBQuanta(q: Quanta) {
    val dbQuanta = DBQuanta(q.title,
                            q.lang,
                            q.year,
                            q.`abstract`,
                            q.url.map(_.toArray),
                            q.fos.map(_.toArray),
                            q.id,
                            q.references.getOrElse(List.empty).toArray)
  }
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

  def insert[F[_]](node: DBQuanta, g: ScalaGraph)(implicit accum: SEEN,
                                                  F: Effect[F]): F[Vertex] = {

    F.delay {
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
  def insertPipe[F[_]: Effect](state: SEEN, g: ScalaGraph): Pipe[F, DBQuanta, Vertex] = {
    in: Stream[F, DBQuanta] =>
      implicit val stateI = state

      in.flatMap(q => Stream.eval[F, Vertex](insert(q, g)))
  }

}
