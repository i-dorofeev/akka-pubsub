name := "broker"

version := "0.1"

scalaVersion := "2.12.4"

libraryDependencies ++= {
  val akkaVersion = "2.5.9"

  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion
  )
}