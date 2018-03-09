import sbt._

object Dependencies {

  def akka(moduleName: String): ModuleID = "com.typesafe.akka" %% ("akka-" + moduleName) % "2.5.11"

  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val h2database = "com.h2database" % "h2" % "1.4.196"
}