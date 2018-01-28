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
    log.debug("Initializing...")
    BrokerDatabase.initialize()
        .onComplete {
          case Success(_) =>
            self ! "initialized"
          case Failure(ex) =>
            log.error("Failed to initialize database: {}", ex)
        }
  }

  override def postStop(): Unit = {
    BrokerDatabase.shutdown()
  }

  override def receive: Receive = init

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
