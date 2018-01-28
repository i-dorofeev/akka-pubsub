name := "broker"

version := "0.1"

scalaVersion := "2.12.4"

resolvers += Resolver.jcenterRepo

// akka
libraryDependencies ++= {
  def akka(moduleName: String): ModuleID = "com.typesafe.akka" %% ("akka-" + moduleName) % "2.5.9"

  Seq(
    akka("actor"),
    akka("cluster"),
    akka("slf4j"),
    akka("testkit") % Test,

    "ch.qos.logback" % "logback-classic" % "1.2.3",

    "org.scalactic" %% "scalactic" % "3.0.4",
    "org.scalatest" %% "scalatest" % "3.0.4" % Test
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