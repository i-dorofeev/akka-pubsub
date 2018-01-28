import BrokerActor.{Event, EventAck}
import akka.actor.{Actor, ActorLogging, ActorRef}

case class PublisherMessage(id: Int, value: String)

class PublisherActor(val broker: ActorRef) extends Actor with ActorLogging {

  override def receive: Receive = {
    case PublisherMessage(id, value) =>
      broker ! Event("publisher", id, s"$id - $value")

    case ack: EventAck =>
      log.debug("Received {}", ack)
  }
}
