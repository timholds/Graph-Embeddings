import java.util.concurrent.ConcurrentHashMap

import cats.effect._
import datastream.QuantaStream
import datastream.Quanta
import fs2.StreamApp.ExitCode
import fs2._
import fs2.async.mutable.{Signal, Topic}
import fs2.async
import gremlin.scala._
import org.apache.commons.configuration.BaseConfiguration
import org.janusgraph.core.JanusGraphFactory
import org.janusgraph.graphdb.transaction.StandardJanusGraphTx

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object HowToRun extends App {
//  import util._
//  val cfg = new BaseConfiguration()
//  cfg.setProperty("storage.backend", "inmemory")
//  implicit val jdb = JanusGraphFactory.open(cfg).asScala
//  implicit val accum =
//    new ConcurrentHashMap[String, Either[List[Vertex], Vertex]]
//
//  val s: Stream[IO, Quanta] = new QuantaStream[IO].getQuantaStream.take(5000)
//
//  val res = s.map(_.dbQuanta).through(DBIO.insertPipe(accum, jdb))
//  res.compile.drain.unsafeRunSync()
//  println("done")
//  println("# nodes :" + jdb.V().toList().length)
//  println("# edges :" + jdb.E().toList.length)
//  jdb.V().toList().map(_.toCC[DBQuanta])
  val p = new ParPubSub[IO]
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
      println(node.title)
      tx.tx().commit()
      g.tx().commit()
      res
    }
  }
  def insertPipe[F[_]: Effect](state: SEEN,
                               g: ScalaGraph): Pipe[F, DBQuanta, Vertex] = {
    in: Stream[F, DBQuanta] =>
      implicit val stateI = state

      in.flatMap(q => Stream.eval[F, Vertex](insert(q, g)))
  }

}

class DBSaver[F[_]: Effect](qtopic: Topic[F, Quanta])(implicit
                                                      S: Scheduler,
                                                      accum: util.SEEN,
                                                      jdb: ScalaGraph) {
  def startSubscriber: Stream[F, Vertex] = {
    import util._
    val s1: Stream[F, Quanta] = qtopic.subscribe(10)
    val s2: Stream[F, Vertex] =
      s1.map(_.dbQuanta).through(DBIO.insertPipe(accum, jdb))
    s2
  }
}

class QuantaTopicPusher[F[_]: Effect](qtopic: Topic[F, Quanta])(
    implicit S: Scheduler) {
  def startPublisher(n: Int): Stream[F, Unit] = {
    val str = new QuantaStream[F].getQuantaStream.take(n)
    str.to(qtopic.publish)
  }
}

class ParPubSub[F[_]: Effect] extends StreamApp[F] {
  override def stream(
      args: List[String],
      requestShutdown: F[Unit]): Stream[F, StreamApp.ExitCode] = {
    implicit val accum =
      new ConcurrentHashMap[String, Either[List[Vertex], Vertex]]
    val cfg = new BaseConfiguration()
    cfg.setProperty("storage.backend", "inmemory")
    implicit val jdb = JanusGraphFactory.open(cfg).asScala

    Scheduler(corePoolSize = 10).flatMap { implicit S: fs2.Scheduler =>
      for {
        topic <- Stream.eval(
          async.topic[F, Quanta](
            Quanta(None, None, None, None, None, None, "test", None)))
        pub = new QuantaTopicPusher(topic)

        service = new DBSaver(topic)
        exitCode <- Stream(
          pub.startPublisher(1000).concurrently(service.startSubscriber))
          .join(2)
          .drain ++ Stream.emit(ExitCode.Success)
      } yield exitCode
    }
  }
}

object ParPubSubRunner extends ParPubSub[IO]
