import akka.actor.{ActorSystem, Props}

import scala.io.StdIn

object Main extends App {

  val system = ActorSystem("pubsub-broker")
  val broker = system.actorOf(Props(classOf[BrokerActor]))
  val publisher = system.actorOf(Props(classOf[PublisherActor], broker))
  val subscriber = system.actorOf(Props(classOf[SubscriberActor], broker))

  println("Broker started")

  broker ! Subscribe("dummy")
  broker ! Event("dummy", "some data")

  publisher ! PublisherMessage(1, "msg")

  StdIn.readLine()
  import scala.concurrent.ExecutionContext.Implicits.global
  system.terminate().andThen { case _ => println("Broker stopped") }
}
