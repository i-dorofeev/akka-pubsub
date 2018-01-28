import akka.actor.{ActorSystem, Props}
import akka.cluster.Cluster

import scala.io.StdIn

object Main extends App {

  val system = ActorSystem("pubsub-broker")

  try {
    system.actorOf(Props(classOf[SubscriberActor]), "subscriber")

    println("Subscriber started")

    StdIn.readLine()

  } finally {
    import scala.concurrent.ExecutionContext.Implicits.global
    val cluster = Cluster(system)
    cluster.leave(cluster.selfAddress)
    system.terminate().andThen { case _ => println("Subscriber stopped") }
  }
}