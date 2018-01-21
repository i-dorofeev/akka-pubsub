import akka.actor.{Actor, ActorRef}

class SubscriberActor(val broker: ActorRef) extends Actor {

  override def preStart(): Unit = {
    broker ! Subscribe("publisher")
  }

  override def receive: Receive = {
    case Event(topic, payload) =>
      println(s"Subscriber received event($topic - $payload)")
  }
}
