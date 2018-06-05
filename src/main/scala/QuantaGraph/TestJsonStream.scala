package QuantaGraph

object ExampleStream {


  def main(args: Array[String]): Unit = {

    case class Popularity(repo: Option[String], stars: Option[Int])

    import java.io.File

    def getListOfFiles(dir: String): List[File] = new File(dir).listFiles.filter(_.isFile).toList


    import java.nio.file.Paths

    import cats.effect.IO
    import fs2.{Stream, text}
    import io.circe.Json
    import io.circe.fs2.{decoder, stringStreamParser}
    import io.circe.generic.auto._

    def fileToStream(dataFile: File): Stream[IO, Popularity] = {
      //println("Printing file contents:\n" + Source.fromFile(dataFile, "utf-8").getLines.mkString)

      val stringStream: Stream[IO, String] = fs2.io.file
        .readAll[IO](Paths.get(dataFile.getCanonicalPath), 4096)
        .through(text.utf8Decode)
      //println(stringStream.compile.toList.unsafeRunSync())

      val parsedStream: Stream[IO, Json] = stringStream.through(stringStreamParser)
      //println(parsedStream.compile.toList.unsafeRunSync())

      val popularityStream: Stream[IO, Popularity] = parsedStream.through(decoder[IO, Popularity])
      //println(popularityStream.compile.toList.unsafeRunSync())

      popularityStream
    }


    val projectRoot: String = new java.io.File(".").getCanonicalPath
    val dataFolder: String = projectRoot + "/data"
    val dataFiles: List[File] = getListOfFiles(dataFolder).filter(f => f.getName.endsWith(".json"))

    import scala.concurrent.ExecutionContext.Implicits.global
    val dataStream: Stream[IO,Popularity] = dataFiles.map(fileToStream).reduce((a,b)=>a.merge(b))

    println(dataStream.take(10).compile.toList.unsafeRunSync())

  }
}