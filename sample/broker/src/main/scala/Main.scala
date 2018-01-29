import akka.actor.{ActorSystem, Props}
import akka.cluster.Cluster
import pubsub.BrokerActor

import scala.io.StdIn

object Main extends App {

  val system = ActorSystem("pubsub-broker")

  try {
    val broker = system.actorOf(BrokerActor.props(), "broker")
    val publisher = system.actorOf(Props(classOf[PublisherActor], broker))
    val subscriber = system.actorOf(Props(classOf[SubscriberActor], broker))

    println("Broker started")

    println("Press enter to send a message or q to stop the broker.")

    Iterator.continually(StdIn.readLine)
      .takeWhile(_ != "q")
      .toSeq.zipWithIndex
      .foreach { case (input, id) => publisher ! PublisherMessage(id, input) }

  } finally {
    import scala.concurrent.ExecutionContext.Implicits.global
    val cluster = Cluster(system)
    cluster.leave(cluster.selfAddress)
    system.terminate().andThen { case _ => println("Broker stopped") }
  }
}