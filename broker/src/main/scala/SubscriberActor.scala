import BrokerActor.{Event, Subscribe}
import akka.actor.{Actor, ActorRef}

class SubscriberActor(val broker: ActorRef) extends Actor {

  override def preStart(): Unit = {
    broker ! Subscribe("publisher", 0)
  }

  override def receive: Receive = {
    case Event(topic, _, payload) =>
      println(s"Subscriber received event($topic - $payload)")
  }
}
