import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class HighPerfDbInsertsSimulation extends Simulation {

	val httpConf = http.baseURL("http://localhost:9000")

	object Test {
	
		val names = csv("names.csv").random

	val go = feed(names)
		.exec(
			http("insert")
			.post("/things")
			.body(StringBody("""{"name":"${name}"}""")).asJSON
		)
	}

	val scenario01 = scenario("StressTest").exec(Test.go)
	
	setUp(
		scenario01.inject(
			rampUsersPerSec(10) to(550) during(5 minutes)
		)
	).protocols(httpConf)
}	