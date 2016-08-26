package dimmer.toggles

import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json._
import play.api.mvc._
import play.api.mvc.Results._

import scala.concurrent.Future

object RestUtils {

  def json[A](sample: JsValue)(implicit reader: Reads[A]): BodyParser[A] =
    BodyParser("json reader") { request =>
      import play.api.libs.iteratee.Execution.Implicits.trampoline
      BodyParsers.parse.tolerantJson(request).mapFuture {
        case Left(simpleResult) =>
          Future.successful(Left(simpleResult))
        case Right(jsValue) =>
          jsValue.validate(reader).map { a =>
            Future.successful(Right(a))
          }.recoverTotal { jsError =>
            Future.successful(Left(badJsonResponse(sample, jsError)))
          }
      }
    }

  def badJsonResponse[A](sample: JsValue, jsError: JsError): Result = {
    val errorsObject = JsObject(jsError.errors.map {
      case (path, errors) => path.toString() -> JsArray(errors.map(e => JsString(e.message)))
    })

    BadRequest(Json.obj(
      "status" -> "Bad Request",
      "reason" -> "Provided json body is not valid",
      "remedy" -> "Provide a valid json body of the form given in the sample field",
      "sample" -> sample,
      "errors" -> errorsObject
    ))
  }

  def badHeaderResponse(request: Request[_], allowedHeaders: String*) = {
    import HeaderNames.ACCEPT

    val givenAcceptHeaders = request.acceptedTypes.mkString(", ")
    BadRequest(Json.obj(
      "status" -> "Bad Request",
      "reason" -> s"No supported $ACCEPT request header provided (given was '$givenAcceptHeaders').",
      "remedy" -> s"Include ${allowedHeaders.mkString(", or")} in the $ACCEPT request header"))
  }

  object OnlyJson extends ActionFilter[Request] with HeaderNames with AcceptExtractors {

    override protected def filter[A](request: Request[A]): Future[Option[Result]] = Future.successful {
      request match {
        case Accepts.Json() => None
        case _ => Some(badHeaderResponse(request, MimeTypes.JSON))
      }
    }
  }


  val ActionWithJson = Action andThen OnlyJson
}