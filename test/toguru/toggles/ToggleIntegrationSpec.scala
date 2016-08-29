package toguru.toggles

import toguru.PostgresSetup
import toguru.app.Config
import toguru.toggles.ToggleActor.GetToggle
import play.api.test.Helpers._
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc.Results
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}


class ToggleIntegrationSpec extends PlaySpec
  with BeforeAndAfterAll with Results with PostgresSetup with OneServerPerSuite with FutureAwaits with DefaultAwaitTimeout {

  override def config = app.injector.instanceOf[Config].typesafeConfig

  override def log(message: String): Unit = info(message)

  override protected def beforeAll(): Unit = startPostgres()

  override protected def afterAll(): Unit = stopPostgres()

  def getToggle(name: String): Option[Toggle] = {
    import akka.pattern.ask

    val actor = ToggleActor.provider(app.actorSystem).create(name)
    await((actor ? GetToggle).mapTo[Option[Toggle]])
  }

  def toggleAsString(name: String) =
    s"""{"name" : "$name", "description" : "toggle description", "tags" : {"team" : "Shared Services"}}"""

  "ToggleEndpoint" should {
    "successfully create toggles" in {
      // prepare
      waitForPostgres()

      val name = "toggle name"
      val wsClient = app.injector.instanceOf[WSClient]
      val toggleEndpointURL = s"http://localhost:$port/toggle"
      val body = toggleAsString(name)

      // execute
      val response = await(wsClient.url(toggleEndpointURL).post(body))

      // verify
      response.status mustBe OK
      val json = Json.parse(response.body)

      (json \ "status").asOpt[String] mustBe Some("Ok")

      val maybeToggle = getToggle(name)
      maybeToggle mustBe Some(Toggle(name, "toggle description", Map("team" -> "Shared Services")))
    }

    "reject creating duplicate toggles" in {
      // prepare
      waitForPostgres()

      val name = "toggle name 2"
      val wsClient = app.injector.instanceOf[WSClient]
      val toggleEndpointURL = s"http://localhost:$port/toggle"
      val body = toggleAsString(name)

      // fist request.
      await(wsClient.url(toggleEndpointURL).post(body))

      // execute
      // second request.
      val response = await(wsClient.url(toggleEndpointURL).post(body))

      response.status mustBe CONFLICT
      val json = Json.parse(response.body)
      (json \ "status").asOpt[String] mustBe Some("Conflict")
    }
  }
}
