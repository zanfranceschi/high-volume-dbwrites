import com.typesafe.config.ConfigFactory

object Config {
	
	private val config = ConfigFactory.load()
	
	object amqp {
		val uri = config.getString("amqp.uri")
		val queue = config.getString("amqp.queue")
		val prefecthCount = config.getInt("amqp.prefecthCount")
		val consumers = config.getInt("amqp.consumers")
		val waitingMessagesTimeoutSeconds = config.getInt("amqp.waitingMessagesTimeoutSeconds")
	}
}