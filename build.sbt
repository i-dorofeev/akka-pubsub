import Dependencies._

lazy val commonSettings = Seq(
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "2.12.4",
    resolvers += Resolver.jcenterRepo
)

lazy val pubsub = project
  .settings(
    commonSettings,

    name := "pubsub",

    libraryDependencies ++= Seq(
      akka("actor"),
      akka("stream"),

      "com.typesafe.slick" %% "slick" % "3.2.1",
      "com.typesafe.slick" %% "slick-hikaricp" % "3.2.1",

      "com.typesafe.scala-logging" %% "scala-logging" % "3.8.0",

      // Tests
      akka("slf4j") % Test,
      akka("testkit") % Test,

      logbackClassic % Test,
      "org.scalactic" %% "scalactic" % "3.0.4" % Test,
      "org.scalatest" %% "scalatest" % "3.0.4" % Test,
      "org.scalamock" %% "scalamock" % "4.1.0" % Test,

      h2database % Runtime
    ),

    // enables ordered output for scalatest tests
    logBuffered in Test := false
  )
