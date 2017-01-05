package controllers

import javax.inject._

import com.rabbitmq.client._
import config.Config
import model._
import model.Implicits._
import play.api._
import play.api.db.slick.DatabaseConfigProvider
import play.api.mvc._
import play.api.libs.json._
import slick.driver.JdbcProfile
import slick.driver.PostgresDriver.api._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

@Singleton
class ThingsController @Inject() (
								   dbConfigProvider: DatabaseConfigProvider,
								   configuration: Configuration,
							   		connection: Connection) extends Controller {
	
	val db = dbConfigProvider.get[JdbcProfile].db
	

	def curl = Action {
		Ok("curl -v -H \"Content-Type: application/json\" -d '{\"name\":\"test\"}' http://localhost:9000")
	}
	
	def get = Action.async { implicit request =>
		
		if (!request.queryString.contains("q")) {
			Future {
				val json = JsObject(Seq(
					"message" -> JsString("you must provide the 'q' query string"),
					"_links" -> JsObject(Seq(
						"example" -> JsString("http://localhost:9000?q=test")))))
				BadRequest(Json.prettyPrint(json))
			} map { r => r }
		}
		else {
			val q = request.getQueryString("q").get
			
			db.run(ThingsDAO.things.filter(t => t.name like s"%${q}%").take(10).result) map { t =>
				if (t.size > 0) {
					Ok(Json.prettyPrint(Json.toJson(t)))
				}
				else {
					NotFound(Json.prettyPrint(JsObject(Seq(
						"message" -> JsString(s"nothing found for '${q}'")))))
				}
			}
		}
	}
	
	def post = Action(parse.json[Thing]) { request =>
		
		val requestedThing = request.body
		val responseThing = new Thing(requestedThing.name)
		
		val channel = connection.createChannel()
		
		channel.confirmSelect()
		
		Logger.debug(Json.toJson(responseThing).toString)
		
		channel.basicPublish(
			Config.amqp.exchange,
			Config.amqp.routingKey,
			null,
			Json.toJson(responseThing).toString.getBytes
		)
		
		channel.waitForConfirms()
		channel.close()

		Accepted(Json.toJson(responseThing))
			.withHeaders("Location" -> s"/things/${responseThing.id.get}")
	}
	
	def getThing(id: String) = Action {
		val result = Await.result(db.run(ThingsDAO.things.filter(t => t.id === id).result), 60 seconds)
		if (result.isEmpty) {
			NotFound(Json.prettyPrint(JsObject(Seq(
				"message" -> JsString("thing not found")))))
		}
		else {
			Ok(Json.toJson(result.head))
		}
	}
}