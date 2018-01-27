import BrokerActor.Event
import SubscriptionActor.SubscriptionAck
import akka.actor.{Actor, ActorRef, Props, Stash, Terminated}

import scala.util.{Failure, Success}

object SubscriptionActor {

  case class SubscriptionAck(subscription: ActorRef)

  def props(subscriber: ActorRef, topic: String, eventId: Int): Props =
    Props(new SubscriptionActor(subscriber, topic, eventId))
}

class SubscriptionActor(val subscriber: ActorRef, val topic: String, var eventId: Int) extends Actor with Stash {

  import context._

  override def preStart(): Unit = {
    subscriber ! SubscriptionAck(self)
    context.watch(subscriber)
    BrokerDatabase.fetchEvents(topic, eventId)
      .foreach { evt =>
        println(s"Forwarding $evt to $subscriber")
        subscriber ! evt }
      .onComplete {
        case Success(_) =>
          self ! "subscription initialized"
        case Failure(ex) =>
          println(s"Failed to initialize subscription: $ex")
      }
  }

  private def init: Receive = {
    case "subscription initialized" if sender() == self =>
      unstashAll()
      become(work)
      println("subscription switched to work mode")
  }

  private def work: Receive = {
    case evt: Event =>
      subscriber ! evt

    case Terminated(a) if a == subscriber =>
      context.stop(self)
      println(s"Detected subscriber $subscriber terminated. Cancelling subscription")
  }

  override def receive: Receive = init

}
