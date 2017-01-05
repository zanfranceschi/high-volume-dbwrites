package model

import java.util.UUID
import play.api.libs.json.Json
import slick.lifted.Tag
import slick.driver.PostgresDriver.api._


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

object ThingsDAO {
	val things = TableQuery[ThingsDAO]
}