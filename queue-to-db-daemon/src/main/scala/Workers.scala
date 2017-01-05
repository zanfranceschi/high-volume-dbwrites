import com.rabbitmq.client._
import scalikejdbc._
import scala.concurrent.ExecutionContext.Implicits.global
import argonaut._
import Argonaut._
import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorLogging, OneForOneStrategy, Props, SupervisorStrategy}
import com.rabbitmq.client.AMQP.BasicProperties
import scala.language.postfixOps


object Messages {
	object StartWorking
}

class Dequeuing()(implicit val connection: com.rabbitmq.client.Connection) extends Actor with ActorLogging {
	
	val channel = connection.createChannel()
	
	val consumer = new DefaultConsumer(channel) {
		
		override def handleDelivery(consumerTag: String,
									envelope: Envelope,
									properties: BasicProperties,
									body: Array[Byte]): Unit = {
			try {
				
				val json = new String(body)
				
				val thing = Parse.decodeOption[Thing](json)
				
				if (thing.isDefined) {
					
					val col = Thing.column
					
					withSQL {
						insert.into(Thing).namedValues(
							col.id -> sqls.?,
							col.name -> sqls.?,
							col.status -> sqls.?)
					}
					channel.basicAck(envelope.getDeliveryTag, false)
				}
				else {
					log.warning(s"could not deserialize: ${json}")
					channel.basicNack(envelope.getDeliveryTag, false, false)
				}
			}
			catch {
				case t: Throwable =>
					t.printStackTrace()
					log.error(s"handleDelivery ${t.getMessage}")
					channel.basicNack(envelope.getDeliveryTag, false, false)
			}
		}
	}
	
	def start = {
		channel.basicQos(1, false)
		channel.basicConsume(Config.amqp.queue, false, consumer)
	}
	
	override def receive = {
		case Messages.StartWorking =>
			start
	}
}


class Manager extends Actor with ActorLogging {
	
	def init = {
		val factory = new ConnectionFactory()
		factory.setUri(Config.amqp.uri)
		implicit val connection = factory.newConnection()
		Iterator.range(0, Config.amqp.consumers) foreach { i =>
			val dequeuing = context.actorOf(Props(new Dequeuing()))
			dequeuing ! Messages.StartWorking
			log.info(s"worker ${i} started")
		}
	}
	
	override def supervisorStrategy: SupervisorStrategy = {
		OneForOneStrategy() {
			case t: Throwable =>
				log.error(s"supervisorStrategy: ${t.getMessage}")
				Restart
		}
	}
	
	def receive = {
		case Messages.StartWorking =>
			init
	}
}