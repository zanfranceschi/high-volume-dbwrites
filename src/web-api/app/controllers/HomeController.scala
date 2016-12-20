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
import scala.concurrent.Await
import scala.concurrent.duration._

@Singleton
class HomeController @Inject() (
								   dbConfigProvider: DatabaseConfigProvider,
								   configuration: Configuration,
							   		connection: Connection) extends Controller {
	
	val db = dbConfigProvider.get[JdbcProfile].db
	

	def curl = Action {
		Ok("curl -v -H \"Content-Type: application/json\" -d '{\"name\":\"test\"}' http://localhost:9000")
	}
	
	def get = Action.async {
		db.run(ThingsDAO.things.filter(t => t.name like "%abcd%").result) map { t => Ok(Json.toJson(t)) }
	}
	
	def post = Action(parse.json[Thing]) { request =>
		
		val requestedThing = request.body
		val responseThing = new Thing(requestedThing.name)
		
		val channel = connection.createChannel()
		
		channel.confirmSelect()
		
		Logger.info(Json.toJson(responseThing).toString)
		
		channel.basicPublish(
			Config.amqp.exchange,
			Config.amqp.routingKey,
			null,
			Json.toJson(responseThing).toString.getBytes
		)
		
		Accepted(Json.toJson(responseThing)).withHeaders("Location" -> s"/things/${responseThing.id.get}")
	}
	
	def getThing(id: String) = Action {
		val result = Await.result(db.run(ThingsDAO.things.filter(t => t.id === id).result), 60 seconds)
		if (result.isEmpty)
			NotFound("not found")
		else
			Ok(Json.toJson(result.head))
	}
}