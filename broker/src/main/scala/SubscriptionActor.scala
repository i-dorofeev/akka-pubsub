import BrokerActor.Event
import SubscriptionActor.SubscriptionAck
import akka.actor.{Actor, ActorRef, Props, Terminated}

object SubscriptionActor {

  case class SubscriptionAck(subscription: ActorRef)

  def props(subscriber: ActorRef, topic: String, eventId: Int): Props =
    Props(new SubscriptionActor(subscriber, topic, eventId))
}

class SubscriptionActor(val subscriber: ActorRef, val topic: String, var eventId: Int) extends Actor {

  import context.dispatcher

  override def preStart(): Unit = {
    subscriber ! SubscriptionAck(self)
    context.watch(subscriber)
    BrokerDatabase.fetchEvents(topic, eventId)
      .foreach { evt => subscriber ! evt }
  }

  override def receive: Receive = {
    case evt: Event =>
      subscriber ! evt

    case Terminated(a) if a == subscriber =>
      context.stop(self)
      println(s"Detected subscriber $subscriber terminated. Cancelling subscription")
  }

}
