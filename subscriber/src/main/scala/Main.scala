import akka.actor.{ActorSystem, Props}

import scala.io.StdIn

object Main extends App {

  val system = ActorSystem("pubsub-broker")

  println("Subscriber started")

  StdIn.readLine()
  import scala.concurrent.ExecutionContext.Implicits.global
  system.terminate().andThen { case _ => println("Subscriber stopped") }
}