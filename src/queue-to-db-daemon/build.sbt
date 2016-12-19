name := """hello-akka"""

version := "1.0"

scalaVersion := "2.11.6"

lazy val akkaVersion = "2.4.0"

libraryDependencies ++= Seq(
	"com.typesafe.akka" %% "akka-actor" % akkaVersion,
	"com.typesafe.akka" %% "akka-testkit" % akkaVersion,
	"org.scalatest" %% "scalatest" % "2.2.4" % "test",
	"com.rabbitmq" % "amqp-client" % "4.0.0",
	//"junit" % "junit" % "4.12" % "test",
	"com.novocode" % "junit-interface" % "0.11" % "test",
	
	//"com.typesafe.slick" %% "slick" % "3.1.1",
	//"com.typesafe.slick" %% "slick-hikaricp" % "3.1.1",
	
	"org.scalikejdbc" %% "scalikejdbc"       % "2.5.0",
	"org.scalikejdbc" %% "scalikejdbc-config"  % "2.5.0",
	"com.h2database"  %  "h2"                % "1.4.193",
	//"ch.qos.logback"  %  "logback-classic"   % "1.1.7",
	

	"org.slf4j" % "slf4j-nop" % "1.6.4",
	"org.postgresql" % "postgresql" % "9.4-1206-jdbc42",
	"com.typesafe" % "config" % "1.3.1",

	"io.argonaut" %% "argonaut" % "6.1"

)

testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")


fork in run := true