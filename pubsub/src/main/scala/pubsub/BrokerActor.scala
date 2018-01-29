package pubsub

import akka.actor.{ActorLogging, Props, Stash, Terminated}
import pubsub.BrokerActor.{Event, EventAck, Subscribe}

import scala.concurrent.Future
import scala.util.Success

object BrokerActor {

  case class Subscribe(topic: String, eventId: Int)
  case class Event(topic: String, eventId: Int, payload: String)
  case class EventAck(topic: String)

  def props(): Props = Props(new BrokerActor())
}

class BrokerActor extends InitializingActor
  with Stash
  with ActorLogging {

  private val subscriptions = new Subscriptions

  import context._

  override def init: Future[Unit] = BrokerDatabase.initialize()
  override def postStop: Unit = BrokerDatabase.shutdown()

  override def working: Receive = {
    case Subscribe(topic, eventId) =>
      val subscriptionActor = actorOf(SubscriptionActor.props(sender(), topic, eventId))
      subscriptions.register(topic, subscriptionActor)
      watch(subscriptionActor)

      log.debug("Subscribed {} to topic {}", sender(), topic)

    case evt: Event =>
      log.debug("Received new event {}", evt)

      val publisher = sender()
      BrokerDatabase.persistEvent(evt)
          .andThen { case Success(_) =>
            publisher ! EventAck(evt.topic)
            subscriptions.byTopic(evt.topic).foreach { s => s ! evt }
          }

    case Terminated(subscriptionActor) =>
      subscriptions.unregister(subscriptionActor)
      log.debug("Unregistered SubscriptionActor {}", subscriptionActor)
  }
}


