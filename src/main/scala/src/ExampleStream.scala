package src



object ExampleStream {



//  def main2(args: Array[String]): Unit = {
//
//    import java.nio.file.Paths
//    import cats.effect.IO
//    import fs2.{Stream, text}
//    import io.circe.Json
//    import io.circe.fs2.{stringStreamParser, decoder}
//    import io.circe.generic.auto._
//
//    case class Popularity(repo: String, stars: Int)
//
//    val json = """{"repo": "circe-fs2", "stars": 13}
//                 |{"repo": "circe-yaml", "stars": 32}""".stripMargin.getBytes("UTF-8").iterator
//
//    val stringStream: Stream[IO, String] =
//      Stream.fromIterator[IO, Byte](json).through(text.utf8Decode)
//
//    val parsedStream: Stream[IO, Json] = stringStream.through(stringStreamParser)
//
//    val popularityStream: Stream[IO, Popularity] = parsedStream.through(decoder[IO, Popularity])
//
//    println(popularityStream.compile.toList.unsafeRunSync())
//
//  }

  def main(args: Array[String]): Unit = {

    case class Popularity(repo: String, stars: Int)

    import java.nio.file.Paths
    import cats.effect.IO
    import fs2.{Stream, text}
    import io.circe.Json
    import io.circe.fs2.{stringStreamParser, stringArrayParser, decoder}
    import io.circe.generic.auto._

    val projectRoot: String = new java.io.File(".").getCanonicalPath
    val dataFolder: String = projectRoot + "/data"
    val dataFile: String = dataFolder + "/popularity.json"

    import scala.io.Source
    println("Printing file contents:\n" + Source.fromFile(dataFile, "utf-8").getLines.mkString)


    // Not sure that these streams actually contain anything
    val stringStream: Stream[IO, String] = fs2.io.file.readAll[IO](Paths.get(dataFile), 4096).through(text.utf8Decode)
    println(stringStream.compile.toList.unsafeRunSync())

    val parsedStream: Stream[IO, Json] = stringStream.through(stringStreamParser)
    println(parsedStream.compile.toList.unsafeRunSync())


    val popularityStream: Stream[IO, Popularity] = parsedStream.through(decoder[IO, Popularity])
    println(popularityStream.compile.toList.unsafeRunSync())
  }
}