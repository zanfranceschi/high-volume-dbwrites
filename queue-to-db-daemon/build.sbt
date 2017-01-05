name := """daemon"""

version := "1.0"

scalaVersion := "2.11.8"

lazy val akkaVersion = "2.4.0"

libraryDependencies ++= Seq(
	"com.typesafe.akka" %% "akka-actor" % akkaVersion,
	"org.scalatest" %% "scalatest" % "2.2.4" % "test",
	"com.rabbitmq" % "amqp-client" % "4.0.0",
	"org.scalikejdbc" %% "scalikejdbc"       % "2.5.0",
	"org.scalikejdbc" %% "scalikejdbc-config"  % "2.5.0",
	"org.slf4j" % "slf4j-nop" % "1.6.4",
	"org.postgresql" % "postgresql" % "9.4-1206-jdbc42",
	"com.typesafe" % "config" % "1.3.1",
	"io.argonaut" %% "argonaut" % "6.1"
)

scalacOptions ++= Seq("-feature")

fork in run := true