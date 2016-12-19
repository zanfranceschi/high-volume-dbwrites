import java.lang.Thread.UncaughtExceptionHandler

import com.rabbitmq.client._
import scalikejdbc._

import scala.concurrent.ExecutionContext.Implicits.global
import argonaut._
import Argonaut._
import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, Props, SupervisorStrategy}
import com.rabbitmq.client.AMQP.BasicProperties
import com.typesafe.config.ConfigFactory
import java.sql.DriverManager
import java.sql.Connection

import org.joda.time.{DateTime, Period, PeriodType}

import scala.concurrent.duration._

class Dequeuing()(implicit val connection: com.rabbitmq.client.Connection) extends Actor with ActorLogging {
	
	var thingsToInsert = Map[Thing, Long]()
	
	val channel = connection.createChannel()
	
	var timer = context.system.scheduler.scheduleOnce(2 seconds, self, "time's up")
	
	def setTimer = {
		timer = context.system.scheduler.scheduleOnce(2 seconds, self, "time's up")
	}
	
	val bufferSize = 10000
	
	val consumer = new DefaultConsumer(channel) {
		
		override def handleDelivery(consumerTag: String,
									envelope: Envelope,
									properties: BasicProperties,
									body: Array[Byte]): Unit = {
			try {
				
				while(!dequeue) {
					println("blocking...")
				}
				
				timer.cancel()
				
				val json = new String(body)
				val thing = Parse.decodeOption[Thing](json)
				
				if (thing.isDefined) {
					
					// aggregate messages
					thingsToInsert += thing.get -> envelope.getDeliveryTag
					
					if (thingsToInsert.size >= bufferSize) {
						// batch insert if list of messages has the same size of prefetch count
						persistThings
						
					}
				}
				else {
					log.warning(s"could not deserialize: ${json}")
					channel.basicNack(envelope.getDeliveryTag, false, false)
				}
			}
			catch {
				case t: Throwable =>
					log.error(t.getMessage)
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
					log.warning(s"batch didn't work, falling back to individual inserts.")
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
								log.error(s"could not insert ${d._1.asJson} - deliveryTag: ${d._2}")
						}
					}
					log.info(s"individual inserts finished.")
			}
			finally {
				thingsToInsert = Map[Thing, Long]()
			}
		}
	}
	
	def start = {
		channel.basicQos(bufferSize, false)
		channel.basicConsume("thing.created.exp", false, consumer)
	}
	
	var dequeue = true
	
	override def receive = {
		case "start working" =>
			log.debug("let's dequeue")
			start
		case "time's up" =>
			dequeue = false
			log.debug("time's up")
			persistThings
			dequeue = true
	}
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
	
	scalikejdbc.config.DBs.setupAll()
	
	val system = ActorSystem("helloakka")
	val manager = system.actorOf(Props[Manager], "manager")
	manager ! "start working"
	
	/*
	val conn: java.sql.Connection = ConnectionPool.borrow()
	val id = "0"
	val t = Thing.syntax("t")
	val things: List[Thing] = DB readOnly { implicit session =>
		session.fetchSize(5000)
		withSQL {
			select.from(Thing as t).where.ne(t.id, "0").limit(10)
		}.map(Thing(t.resultName)).list.apply()
	}
	
	things foreach { thing =>
		println(thing.asJson)
	}
	
	val col = Thing.column
	val francisco = DB localTx { implicit session =>
		session.fetchSize(1000)
		/*
		withSQL {
			insert.into(Thing).namedValues(
				col.id -> "FAZ",
				col.name -> "Francisco",
				col.status -> "created")
		}.update.apply()
		*/
		val batchParams: Seq[Seq[String]] =
			(0 to 1000000).map(i => Seq(s"B${i}", s"nome ${i}", "created"))
		
		withSQL {
			insert.into(Thing).namedValues(
				col.id -> sqls.?,
				col.name -> sqls.?,
				col.status -> sqls.?)
		}.batch(batchParams: _*).apply()
		
		withSQL {
			select.from(Thing as t).where.eq(t.id, "FAZ")
		}.map(Thing(t.resultName)).single.apply()
	}
	
	if (francisco.isDefined)
		println(francisco.get.name)
	
	//val system = ActorSystem("helloakka")
	//val manager = system.actorOf(Props[Manager], "manager")
	//manager ! "start working"
	*/
}


case class Thing(id: String, name: String, status: String) {
	def this(id: String, name: String) = this(id, name, "created")
}

object Thing extends SQLSyntaxSupport[Thing] {
	
	implicit def ThingCodecJson: CodecJson[Thing] = casecodec3(Thing.apply, Thing.unapply)("id", "name", "status")
	
	def apply(t: ResultName[Thing])(rs: WrappedResultSet): Thing = {
		new Thing(id = rs.get(t.id), name = rs.get(t.name), status = rs.get(t.status))
	}
}