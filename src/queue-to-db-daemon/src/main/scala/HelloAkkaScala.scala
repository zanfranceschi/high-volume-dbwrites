/*
import com.rabbitmq.client._

import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global

import argonaut._
import Argonaut._

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, Props, SupervisorStrategy}

import com.rabbitmq.client.AMQP.BasicProperties
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.util.{Failure, Success}


class Dequeuing()(implicit val connection: Connection) extends Actor with ActorLogging {
	
	val db = Database.forConfig("db")
	
	val things = TableQuery[ThingsDAO]
	
	val channel = connection.createChannel()
	
	var thingsToInsert = List[Thing]()
	
	val bufferSize = 1000
	
	val consumer = new DefaultConsumer(channel) {
		
		override def handleDelivery(consumerTag: String,
									envelope: Envelope,
									properties: BasicProperties,
									body: Array[Byte]): Unit = {
			try {
				val json = new String(body)
				val thing = Parse.decodeOption[Thing](json)
				
				if (thing.isDefined) {
					
					thingsToInsert = thingsToInsert :+ thing.get
					
					val messageCount = channel.messageCount("thing.created.exp")
					
					if (thingsToInsert.size >= bufferSize || bufferSize >= messageCount) {
						
						log.debug("here we go...")
						
						implicit val timeout = 30 seconds
						
						val action = (things ++= thingsToInsert).transactionally
						
						db.run(action) onComplete {
							case Success(value) =>
							
								channel.basicAck(envelope.getDeliveryTag, true)
								thingsToInsert = List[Thing]()
								log.debug("sucesso!")
							
							case Failure(e) =>
								
								log.debug("falling back to individual inserts! Shit!!!")
								
								thingsToInsert foreach { thingToInsert =>
									db.run(things += thingToInsert) onComplete {
										case Success(s) =>
											channel.basicAck(envelope.getDeliveryTag, false)
										case Failure(e2) =>
											channel.basicNack(envelope.getDeliveryTag, false, false)
											//log.error(s"${thingToInsert.asJson.toString} could not be inserted: ${e2.getMessage}")
									}
								}

								thingsToInsert = List[Thing]()
						}
					}
				}
				else {
					log.warning(s"could not deserialize: ${json}")
					channel.basicReject(envelope.getDeliveryTag, false)
				}
			}
			catch {
				case t: Throwable =>
					log.error(t.getMessage)
			}
		}
	}
	
	def init = {
		channel.basicQos(bufferSize, false)
		channel.basicConsume("thing.created.exp", false, consumer)
		log.debug("initted...")
	}
	
	override def receive = {
		case "start working" =>
			log.debug("let's dequeue")
			init
	}
	
	@scala.throws[Exception](classOf[Exception])
	override def postRestart(reason: Throwable): Unit = log.error(s"just recovered from a shit: ${reason.getMessage}")
	
}



class Manager extends Actor with ActorLogging {
	
	def init = {
		val config = ConfigFactory.load()
		val factory = new ConnectionFactory()
		factory.setUri(config.getString("amqp.uri"))
		implicit val connection = factory.newConnection()
		
		for (i <- Iterator.range(0, 5)) {
			val dequeuing = context.actorOf(Props(new Dequeuing()))
			dequeuing ! "start working"
		}
	}
	
	override def supervisorStrategy: SupervisorStrategy = {
		OneForOneStrategy() {
			case t: Throwable => Restart
		}
	}
	
	def receive = {
		case "start working" =>
			log.debug("let's manage...")
			init
	}
}

object Test extends App {
	
	val system = ActorSystem("helloakka")
	val manager = system.actorOf(Props[Manager], "manager")
	manager ! "start working"
}


case class Thing(id: String, name: String, status: String) {
	def this(id: String, name: String) = this(id, name, "created")
}

object Thing {
	implicit def ThingCodecJson: CodecJson[Thing] = casecodec3(Thing.apply, Thing.unapply)("id", "name", "status")
}

class ThingsDAO(tag: Tag) extends Table[Thing](tag, "thing") {
	def id = column[String]("id", O.PrimaryKey)
	def name = column[String]("name")
	def status = column[String]("status")

	def * = (id, name, status) <> ((Thing.apply _).tupled, Thing.unapply _)
}
*/