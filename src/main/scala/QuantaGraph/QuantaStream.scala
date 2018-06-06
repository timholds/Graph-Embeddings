package QuantaGraph

object QuantaStream {

  import java.io.File
  import java.nio.file.Paths

  import cats.effect.IO
  import fs2.{Stream, text}
  import io.circe.Json
  import io.circe.fs2.{decoder, stringStreamParser}
  import io.circe.generic.auto._

  case class Quanta(title: Option[String],
                    lang: Option[String],
                    year: Option[Int],
                    `abstract`: Option[String],
                    url: Option[List[String]],
                    fos: Option[List[String]],
                    id: String,
                    references: Option[List[String]]
                   )

  def getListOfFiles(dir: String): List[File] =
    new File(dir).listFiles.filter(_.isFile).toList

  def fileToStream(dataFile: File): Stream[IO, Quanta] = {
    //import scala.io.Source
    //println("Printing file contents:\n" + Source.fromFile(dataFile, "utf-8").getLines.mkString)

    val stringStream: Stream[IO, String] = fs2.io.file
      .readAll[IO](Paths.get(dataFile.getCanonicalPath), 4096)
      .through(text.utf8Decode)
    //println(stringStream.compile.toList.unsafeRunSync())

    val parsedStream: Stream[IO, Json] =
      stringStream.through(stringStreamParser)
    //println(parsedStream.compile.toList.unsafeRunSync())

    val popularityStream: Stream[IO, Quanta] =
      parsedStream.through(decoder[IO, Quanta])
    //println(popularityStream.compile.toList.unsafeRunSync())

    popularityStream
  }

  val projectRoot: String = new java.io.File(".").getCanonicalPath
  val dataFolder: String = projectRoot + "/data"
  val dataFiles: List[File] =
    getListOfFiles(dataFolder).filter(f => f.getName.endsWith(".test.txt"))

  import scala.concurrent.ExecutionContext.Implicits.global

  val quantaStream: Stream[IO, Quanta] =
    dataFiles.map(fileToStream).reduce((a, b) => a.merge(b))

  def getQuantaStream: Stream[IO, Quanta] = quantaStream

  //println(quantaStream.compile.toList.unsafeRunSync())

}
