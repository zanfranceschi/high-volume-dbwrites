import com.rabbitmq.client._
import scalikejdbc._
import scala.concurrent.ExecutionContext.Implicits.global
import argonaut._
import Argonaut._
import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorLogging, OneForOneStrategy, Props, SupervisorStrategy}
import com.rabbitmq.client.AMQP.BasicProperties
import scala.language.postfixOps
import scala.concurrent.duration._


object Messages {
	object StartWorking
}


class Dequeuing()(implicit val connection: com.rabbitmq.client.Connection) extends Actor with ActorLogging {
	
	val timesupMessage = "time's up"
	
	val channel = connection.createChannel()
	
	var thingsToInsert = Map[Thing, Long]()
	
	var timer = context.system.scheduler.scheduleOnce(Config.amqp.waitingMessagesTimeoutSeconds seconds, self, timesupMessage)
	
	def setTimer = {
		timer = context.system.scheduler.scheduleOnce(Config.amqp.waitingMessagesTimeoutSeconds seconds, self, timesupMessage)
	}
	
	val consumer = new DefaultConsumer(channel) {
		
		override def handleDelivery(consumerTag: String,
									envelope: Envelope,
									properties: BasicProperties,
									body: Array[Byte]): Unit = {
			try {
				timer.cancel()
				val json = new String(body)
				val thing = Parse.decodeOption[Thing](json)
				
				if (thing.isDefined) {
					
					synchronized {
						thingsToInsert += thing.get -> envelope.getDeliveryTag
						if (thingsToInsert.size >= Config.amqp.prefecthCount) {
							persistThings
						}
					}
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
			}
			setTimer
		}
	}
	
	def persistThings = {
		
		val col = Thing.column
		
		val thingsParams: Seq[Seq[Any]] = thingsToInsert.map(d =>
			Seq(d._1.id, d._1.name, d._1.status)
		).to[Seq]
		
		DB localTx { implicit session =>
			try {
				log.debug("trying batch")
				withSQL {
					insert.into(Thing).namedValues(
						col.id -> sqls.?,
						col.name -> sqls.?,
						col.status -> sqls.?)
				}.batch(thingsParams: _*).apply()
				channel.basicAck(thingsToInsert.values.max, true)
				log.debug(s"batch ok: ${thingsToInsert.size} entries")
			}
			catch {
				case t: Throwable =>
					log.warning(s"batch didn't work, falling back to individual inserts. (${t.getMessage})")
					thingsToInsert foreach { d =>
						try {
							withSQL {
								insert.into(Thing).namedValues(
									col.id -> d._1.id,
									col.name -> d._1.name,
									col.status -> d._1.status)
							}.update.apply()
							channel.basicAck(d._2, false)
						}
						catch {
							case it: Throwable =>
								channel.basicNack(d._2, false, false)
								log.error(s"could not insert ${d._1.asJson}. (${it.getMessage})")
						}
					}
					log.info(s"individual inserts finished")
			}
			finally {
				thingsToInsert = Map[Thing, Long]()
			}
		}
	}
	
	def start = {
		channel.basicQos(Config.amqp.prefecthCount, false)
		channel.basicConsume(Config.amqp.queue, false, consumer)
	}
	
	override def receive = {
		case Messages.StartWorking =>
			start
		case timesupMessage =>
			log.debug("time's up")
			persistThings
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