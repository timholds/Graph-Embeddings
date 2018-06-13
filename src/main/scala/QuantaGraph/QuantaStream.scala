package datastream

import java.io.File
import java.nio.file.Paths

import cats.effect.Effect
import fs2.{Stream, text}
import io.circe.Json
import io.circe.fs2.{decoder, stringStreamParser}
import io.circe.generic.auto._

case class Quanta(title: Option[String],
                  lang: Option[String],
                  year: Option[Int],
                  references: Option[List[String]],
                  `abstract`: Option[String],
                  url: Option[List[String]],
                  id: String,
                  fos: Option[List[String]],
                  venue: Option[String],
                  n_citation: Option[Int],
                  keywords: Option[List[String]],
                  page_stat: Option[String],
                  page_end: Option[String],
                  publisher: Option[String],
                  volume: Option[String],
                  issue: Option[String],
                  issn: Option[String],
                  isbn: Option[String],
                  doi: Option[String],
                  pdf: Option[String])



class QuantaStream[F[_]]()(implicit F: Effect[F]) {



  def getListOfFiles(dir: String): List[File] =
    new File(dir).listFiles.filter(_.isFile).toList

  def fileToStream(dataFile: File): Stream[F, Quanta] = {
    //import scala.io.Source
    //println("Printing file contents:\n" + Source.fromFile(dataFile, "utf-8").getLines.mkString)

    val stringStream: Stream[F, String] = fs2.io.file
      .readAll[F](Paths.get(dataFile.getCanonicalPath), 4096)
      .through(text.utf8Decode)
    //println(stringStream.compile.toList.unsafeRunSync())

    val parsedStream: Stream[F, Json] =
      stringStream.through(stringStreamParser)
    //println(parsedStream.compile.toList.unsafeRunSync())

    val popularityStream: Stream[F, Quanta] =
      parsedStream.through(decoder[F, Quanta])
    //println(popularityStream.compile.toList.unsafeRunSync())

    popularityStream
  }

  val projectRoot: String = new java.io.File(".").getCanonicalPath
  val dataFolder: String = projectRoot + "/data/test"
  val dataFiles: List[File] =
    getListOfFiles(dataFolder).filter(f => f.getName.endsWith(".txt"))

  import scala.concurrent.ExecutionContext.Implicits.global

  val quantaStream: Stream[F, Quanta] =
    dataFiles.map(fileToStream).reduce((a, b) => a.merge(b))

  def getQuantaStream: Stream[F, Quanta] = quantaStream

  //println(quantaStream.compile.toList.unsafeRunSync())

}
