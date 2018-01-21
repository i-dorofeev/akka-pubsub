import akka.actor.{Actor, ActorRef}

case class PublisherMessage(id: Int, value: String)

class PublisherActor(val broker: ActorRef) extends Actor {

  override def receive: Receive = {
    case PublisherMessage(id, value) =>
      broker ! Event("publisher", s"$id - $value")
  }
}
