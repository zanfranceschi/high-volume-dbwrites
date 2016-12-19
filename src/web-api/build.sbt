name := """web-api"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
	cache,
	ws,
	"org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test,
	"org.postgresql" % "postgresql" % "9.4-1206-jdbc42",
	"com.typesafe.play" %% "play-slick" % "2.0.0",
	"com.rabbitmq" % "amqp-client" % "4.0.0"
)

