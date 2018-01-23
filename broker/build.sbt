name := "broker"

version := "0.1"

scalaVersion := "2.12.4"

resolvers += Resolver.jcenterRepo

// akka
libraryDependencies ++= {
  val akkaVersion = "2.5.9"

  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % akkaVersion
  )
}

// slick
libraryDependencies ++= {
  Seq(
    "com.typesafe.slick" %% "slick" % "3.2.1",
    "org.slf4j" % "slf4j-nop" % "1.6.4",
    "com.typesafe.slick" %% "slick-hikaricp" % "3.2.1",
    "io.github.nafg" %% "slick-migration-api" % "0.4.2"
  )
}

libraryDependencies ++= Seq(
  "com.h2database" % "h2" % "1.4.196" % Runtime
)