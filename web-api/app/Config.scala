package config

import com.typesafe.config.ConfigFactory

object Config {
	
	private val config = ConfigFactory.load()
	
	object amqp {
		val exchange = config.getString("amqp.exchange")
		val routingKey = config.getString("amqp.routingKey")
	}
}