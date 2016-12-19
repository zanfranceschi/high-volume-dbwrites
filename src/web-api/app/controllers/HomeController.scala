package controllers

import java.util.UUID
import javax.inject._

import com.rabbitmq.client._
import play.api._
import play.api.db.slick.DatabaseConfigProvider
import play.api.mvc._
import play.api.libs.json._
import slick.driver.JdbcProfile
import slick.lifted.Tag
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class HomeController @Inject()(dbConfigProvider: DatabaseConfigProvider, configuration: Configuration) extends Controller {
	
	val dbConfig = dbConfigProvider.get[JdbcProfile]
	
	val factory = new ConnectionFactory()
	factory.setUri(configuration.getString("amqp.uri").get)
	val connection = factory.newConnection()
	
	implicit val thingJsonWrites = Json.writes[Thing]
	implicit val thingJsonReads = Json.reads[Thing]
	
	val things = TableQuery[ThingsDAO]
	
	def index = Action.async { implicit request =>
		
		dbConfig.db.run(things.filter(t => t.name like "%abcdef%").result).map { t => Ok(Json.toJson(t)) }
	}
	
	def post = Action(parse.json[Thing]) { request =>
		
		val requestedThing = request.body
		val responseThing = new Thing(requestedThing.name)
		
		val channel = connection.createChannel()
		
		channel.confirmSelect()
		
		Logger.info(Json.toJson(responseThing).toString)
		
		channel.basicPublish(
			configuration.getString("amqp.exchange").get,
			configuration.getString("amqp.routing-key").get,
			null,
			Json.toJson(responseThing).toString.getBytes
		)
		
		Ok(Json.toJson(responseThing))
	}
	
}

object Implicits {
	implicit val thingJsonWrites = Json.writes[Thing]
	implicit val thingJsonReads = Json.reads[Thing]
}

case class Thing(id: Option[String], name: String, status: Option[String]) {
	def this(name: String) = this(Some(UUID.randomUUID.toString), name, Some("created"))
}


class ThingsDAO(tag: Tag) extends Table[Thing](tag, "thing") {
	def id = column[Option[String]]("id", O.PrimaryKey)
	def name = column[String]("name")
	def status = column[Option[String]]("status")
	
	def * = (id, name, status) <> ((Thing.apply _).tupled, Thing.unapply _)
}