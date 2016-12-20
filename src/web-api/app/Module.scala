import com.google.inject.AbstractModule
import com.rabbitmq.client._
import com.typesafe.config.ConfigFactory

class Module extends AbstractModule {
	
	val config = ConfigFactory.load()
	val factory = new ConnectionFactory()
	factory.setUri(config.getString("amqp.uri"))
	val connection = factory.newConnection()
	
	override def configure() = {
		bind(classOf[Connection]).toInstance(connection)
	}
}
