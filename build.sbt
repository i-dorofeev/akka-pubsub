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

      "com.typesafe.slick" %% "slick" % "3.2.1",
      "com.typesafe.slick" %% "slick-hikaricp" % "3.2.1",

      // Tests
      akka("slf4j") % Test,
      akka("testkit") % Test,

      logbackClassic % Test,
      "org.scalactic" %% "scalactic" % "3.0.4" % Test,
      "org.scalatest" %% "scalatest" % "3.0.4" % Test,

      h2database % Runtime
    ))

lazy val broker = project
  .settings(
    commonSettings,

    name := "broker",

    libraryDependencies ++= Seq(
      akka("cluster"),
      akka("slf4j"),

      logbackClassic,
      h2database % Runtime
    ))
  .dependsOn(pubsub)

lazy val subscriber = project
  .settings(
    commonSettings,

    name := "subscriber",

    libraryDependencies ++= Seq(
      akka("cluster"),
      akka("slf4j"),

      logbackClassic,
      h2database % Runtime
    ))
  .dependsOn(pubsub)