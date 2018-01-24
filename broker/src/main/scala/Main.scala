import akka.actor.{ActorSystem, Props}
import akka.cluster.Cluster

import scala.io.StdIn

object Main extends App {

  val system = ActorSystem("pubsub-broker")

  try {
    val broker = system.actorOf(Props(classOf[BrokerActor]), "broker")
    val publisher = system.actorOf(Props(classOf[PublisherActor], broker))
    val subscriber = system.actorOf(Props(classOf[SubscriberActor], broker))

    println("Broker started")

    println("Press enter to send a message")
    StdIn.readLine()
    publisher ! PublisherMessage(1, "msg")

    StdIn.readLine()
  } finally {
    import scala.concurrent.ExecutionContext.Implicits.global
    val cluster = Cluster(system)
    cluster.leave(cluster.selfAddress)
    system.terminate().andThen { case _ => println("Broker stopped") }
  }
}
