package pubsub

import akka.actor.{ActorRef, Props, Terminated}
import pubsub.BrokerActor.Event
import pubsub.SubscriptionActor.SubscriptionAck

import scala.concurrent.Future

object SubscriptionActor {

  case class SubscriptionAck(subscription: ActorRef)

  def props(db: BrokerDatabaseSchema, subscriber: ActorRef, topic: String, eventId: Int): Props =
    Props(new SubscriptionActor(db, subscriber, topic, eventId))
}

class SubscriptionActor(val db: BrokerDatabaseSchema, val subscriber: ActorRef, val topic: String, var eventId: Int) extends InitializingActor {

  import context._

  override def init: Future[Unit] = {
    subscriber ! SubscriptionAck(self)
    context.watch(subscriber)

    db.fetchEvents(topic, eventId).foreach(forward)
  }

  private def forward(evt: Event): Unit = {
    log.debug("Forwarding {} to {}", evt, subscriber)
    subscriber ! evt
  }

  override def working: Receive = {
    case evt: Event =>
      forward(evt)

    case Terminated(a) if a == subscriber =>
      context.stop(self)
      log.debug("Subscriber {} terminated. Subscription cancelled", subscriber)
  }
}
