import akka.actor.{ActorSystem, Props}

object Application extends App {

	scalikejdbc.config.DBs.setupAll()
	
	val system = ActorSystem("System")
	val manager = system.actorOf(Props[Manager], "Manager")
	manager ! Messages.StartWorking
}
