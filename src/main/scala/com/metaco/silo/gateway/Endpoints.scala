package com.metaco.silo.gateway

import cats.Alternative.ops.toAllAlternativeOps
import cats.data.NonEmptyList
import cats.implicits.catsSyntaxAlternativeSeparate
import com.metaco.silo.gateway.Library.Author
import sttp.tapir._
import io.circe.generic.auto._
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.model.{CommaSeparated, Delimited}
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir.ZServerEndpoint
import zio.Task
import zio.ZIO

import scala.util.{Failure, Success, Try}

object Endpoints {
  case class User(name: String) extends AnyVal
  val helloEndpoint: PublicEndpoint[User, Unit, String, Any] = endpoint.get
    .in("hello")
    .in(query[User]("name"))
    .out(stringBody)
  val helloServerEndpoint: ZServerEndpoint[Any, Any] = helloEndpoint.serverLogicSuccess(user => ZIO.succeed(s"Hello ${user.name}"))

  private val regEx = raw""",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$$)""".r

  def parseCommasQuotedString(s: String): Option[List[String]] = {
    // TODO parsing of incoming strings validated for like security stuff?!
    Some(
      regEx
        .split(s)
        .map {
          _.trim.replaceAll("^\"", "").replaceAll("\"$", "")
        }
        .filterNot(_.isBlank)
        .toList
    )

  }

  implicit val roleFilterArrayFilterValues: Codec[List[String], Option[List[List[Author]]], TextPlain] = {

    def encodeMuArg(roles: Option[List[List[Author]]]): List[String] = {
      for {
        filterValueRoles <- roles.toList
        filterValueRole <- filterValueRoles
      } yield filterValueRole.mkString(",")

    }

    def decodeMuArg(ss: List[String]): DecodeResult[Option[List[List[Author]]]] = {
      DecodeResult.Value(Some(ss.map { s =>
        parseCommasQuotedString(s) match {
          case Some(value) =>
            value.map(Author.apply)
          case None =>
            List(Author(s))
        }
      }))

    }

    Codec.list(Codec.string).mapDecode(decodeMuArg)(encodeMuArg)
  }

  import Library.Book

  implicit val myIdCodec: Codec[String, Author, TextPlain] = {

    def decode(s: String): DecodeResult[Author] = Author.parse(s) match {
      case Success(v) => DecodeResult.Value(v)
      case Failure(f) => DecodeResult.Error(s, f)
    }

    def encode(id: Author): String = id.toString
    Codec.string.mapDecode(decode)(encode)
  }

  val booksListing = endpoint.get
    .in("books" / "list" / "all")
    .in(query[Option[List[List[Author]]]]("name"))
    .out(jsonBody[List[Book]])

  val booksListingServerEndpoint: ZServerEndpoint[Any, Any] = booksListing.serverLogicSuccess(s => ZIO.succeed(Library.books))

  // This is ok..
  val c = implicitly[Codec[String, CommaSeparated[Author], TextPlain]].map(_.values)(Delimited(_))

  // but wrapped in option list it's not!
  val books2Listing = endpoint.get
    .in("books" / "list" / "qed")
    .in(query[Option[List[CommaSeparated[Author]]]]("name"))
    .out(jsonBody[List[Book]])

  val books2ListingServerEndpoint: ZServerEndpoint[Any, Any] = booksListing.serverLogicSuccess(s => ZIO.succeed(Library.books))

  val apiEndpoints: List[ZServerEndpoint[Any, Any]] = List(helloServerEndpoint, booksListingServerEndpoint, books2ListingServerEndpoint)

  val docEndpoints: List[ZServerEndpoint[Any, Any]] = SwaggerInterpreter()
    .fromServerEndpoints[Task](apiEndpoints, "harmony", "1.0.0")

  val all: List[ZServerEndpoint[Any, Any]] = apiEndpoints ++ docEndpoints
}

object Library {
  case class Author(name: String) {
    override def toString(): String = name
  }
  object Author {
    def parse(id: String): Try[Author] = {
      Success(new Author(id))
    }
  }

  case class Book(title: String, year: Int, author: Author)

  val books = List(
    Book("The Sorrows of Young Werther", 1774, Author("Johann Wolfgang von Goethe")),
    Book("On the Niemen", 1888, Author("Eliza Orzeszkowa")),
    Book("The Art of Computer Programming", 1968, Author("Donald Knuth")),
    Book("Pharaoh", 1897, Author("Boleslaw Prus"))
  )
}
