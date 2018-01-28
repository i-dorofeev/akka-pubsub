package pubsub

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash, Terminated}
import pubsub.BrokerActor.Event
import pubsub.SubscriptionActor.SubscriptionAck

import scala.util.{Failure, Success}

object SubscriptionActor {

  case class SubscriptionAck(subscription: ActorRef)

  def props(subscriber: ActorRef, topic: String, eventId: Int): Props =
    Props(new SubscriptionActor(subscriber, topic, eventId))
}

class SubscriptionActor(val subscriber: ActorRef, val topic: String, var eventId: Int) extends Actor
  with Stash
  with ActorLogging {

  import context._

  override def preStart(): Unit = {
    log.debug("Initializing...")

    subscriber ! SubscriptionAck(self)
    context.watch(subscriber)

    BrokerDatabase.fetchEvents(topic, eventId).foreach(forward)
      .onComplete {
        case Success(_) =>
          self ! "initialized"
        case Failure(ex) =>
          log.error("Failed to initialize subscription", ex)
      }
  }

  private def forward(evt: Event): Unit = {
    log.debug("Forwarding {} to {}", evt, subscriber)
    subscriber ! evt
  }

  private def init: Receive = {
    case "initialized" if sender() == self =>
      unstashAll()
      become(work)
      log.debug("Successfully initialized and switched to work mode")

    case msg =>
      stash()
      log.debug("Initializing... - stashed {}", msg)
  }

  private def work: Receive = {
    case evt: Event =>
      forward(evt)

    case Terminated(a) if a == subscriber =>
      context.stop(self)
      log.debug("Subscriber {} terminated. Subscription cancelled", subscriber)
  }

  override def receive: Receive = init
}
