package pubsub

import akka.actor.{Actor, ActorLogging, Props, Stash, Terminated}
import pubsub.BrokerActor.{Event, EventAck, Subscribe}

import scala.util.{Failure, Success}

object BrokerActor {

  case class Subscribe(topic: String, eventId: Int)
  case class Event(topic: String, eventId: Int, payload: String)
  case class EventAck(topic: String)

  def props(): Props = Props(new BrokerActor())
}

class BrokerActor extends Actor
  with Stash
  with ActorLogging {

  private val subscriptions = new Subscriptions

  import context._

  override def preStart(): Unit = {
    BrokerDatabase.initialize()
        .onComplete {
          case Success(_) =>
            self ! "database initialized"
            log.debug("Database initialized.")
          case Failure(ex) =>
            log.error("Failed to initialize database: {}", ex)
        }
  }

  override def postStop(): Unit = {
    BrokerDatabase.shutdown()
  }

  override def receive: Receive = init

  private def init: Receive = {
    case "database initialized" if sender() == self =>
      unstashAll()
      become(work)
      log.debug("Switched to work mode")

    case msg =>
      stash()
      log.debug("Stashed {}", msg)
  }

  private def work: Receive = {
    case Subscribe(topic, eventId) =>
      val subscriptionActor = actorOf(SubscriptionActor.props(sender(), topic, eventId))
      subscriptions.register(topic, subscriptionActor)
      watch(subscriptionActor)

      log.debug("Subscribed {} to topic {}", sender(), topic)

    case evt: Event =>
      val publisher = sender()
      BrokerDatabase.persistEvent(evt)
          .andThen { case Success(_) =>
            publisher ! EventAck(evt.topic)
            subscriptions.byTopic(evt.topic).foreach { s => s ! evt }
          }
      log.debug("Received new event {}", evt)

    case Terminated(subscriptionActor) =>
      subscriptions.unregister(subscriptionActor)
      log.debug("Unregistered SubscriptionActor {}", subscriptionActor)
  }
}


