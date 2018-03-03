package pubsub.old

import akka.actor.{ActorRef, Props, Terminated}
import pubsub.old.BrokerActor.Event
import pubsub.old.SubscriptionActor.SubscriptionAck

import scala.concurrent.Future

/**
  * Companion object for subscription actor.
  */
object SubscriptionActor {

  /**
    * Acknowledges successful processing of Subscribe request.
    * @param subscription An ActorRef of an actor managing the subscription.
    */
  case class SubscriptionAck(subscription: ActorRef)

  /**
    * Constructs Props for a SubscriptionActor.
    * @param db Database accessor
    * @param subscriber An actor which initiated the subscription
    * @param topic Topic
    * @param eventId Starting event identifier to run the subscription
    * @return Props object for creating a SubscriptionActor
    */
  def props(db: BrokerDatabaseSchema, subscriber: ActorRef, topic: String, eventId: Int): Props =
    Props(new SubscriptionActor(db, subscriber, topic, eventId))
}

/**
  * Maintains the subscription.
  *
  * On subscription initialization it fetches all the pending events starting
  * from eventId and pushes them to the subscriber. Then it forwards all the
  * events to the subscriber. If the subscriber dies, we simply cancel the subscription
  * so the subscriber itself is responsible for resubscribing.
  *
  * TODO: implement reliable forwarding
  *
  * @param db Database accessor
  * @param subscriber Subscriber actor
  * @param topic Event topic
  * @param eventId Starting event identifier to run the subscription
  */
class SubscriptionActor(val db: BrokerDatabaseSchema, val subscriber: ActorRef, val topic: String, var eventId: Int) extends InitializingActor {

  import context._

  override def init: Future[Unit] = {
    // we send ack to the subscriber to confirm the subscription
    subscriber ! SubscriptionAck(self)

    // we going to watch the subscriber so we can cancel the subscription
    // if the subscriber dies
    context.watch(subscriber)

    // forward initial events to the subscriber
    db.fetchEvents(topic, eventId).foreach(forward)
  }

  private def forward(evt: Event): Unit = {
    log.debug("Forwarding {} to {}", evt, subscriber)
    subscriber ! evt
  }

  override def working: Receive = {
    case evt: Event =>
      forward(evt)

    // we receive Terminated message since we are watching our subscriber
    case Terminated(a) if a == subscriber =>
      context.stop(self)
      log.debug("Subscriber {} terminated. Subscription cancelled", subscriber)
  }
}
