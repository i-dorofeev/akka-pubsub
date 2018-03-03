import akka.actor.{Actor, ActorLogging, ActorRef}
import pubsub.old.BrokerActor.{Event, Subscribe}

class SubscriberActor(val broker: ActorRef) extends Actor with ActorLogging {

  override def preStart(): Unit = {
    broker ! Subscribe("publisher", 0)
  }

  override def receive: Receive = {
    case evt: Event =>
      log.debug("Received event {}", evt)
  }
}
