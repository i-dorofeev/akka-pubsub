package pubsub

import akka.actor.{ActorLogging, Props, Stash, Terminated}
import com.typesafe.config.{Config, ConfigFactory}
import pubsub.BrokerActor.{Event, EventAck, ServiceUnavailable, Subscribe}

import scala.concurrent.Future
import scala.util.Success

object BrokerActor {

  case class Subscribe(topic: String, eventId: Int)
  case class Event(topic: String, eventId: Int, payload: String)
  case class EventAck(topic: String)
  case class ServiceUnavailable()

  def props(config: Config = ConfigFactory.load()): Props = Props(new BrokerActor(config))
}

class BrokerActor(val config: Config) extends InitializingActor
  with Stash
  with ActorLogging {

  private val subscriptions = new Subscriptions
  private val db = new BrokerDatabase(config)

  import context._

  override def init: Future[Unit] = db.initialize()
  override def postStop: Unit = db.shutdown()

  override def working: Receive = {
    case Subscribe(topic, eventId) =>
      val subscriptionActor = actorOf(SubscriptionActor.props(db, sender(), topic, eventId))
      subscriptions.register(topic, subscriptionActor)
      watch(subscriptionActor)

      log.debug("Subscribed {} to topic {}", sender(), topic)

    case evt: Event =>
      log.debug("Received new event {}", evt)

      val publisher = sender()
      db.persistEvent(evt)
          .andThen { case Success(_) =>
            publisher ! EventAck(evt.topic)
            subscriptions.byTopic(evt.topic).foreach { s => s ! evt }
          }

    case Terminated(subscriptionActor) =>
      subscriptions.unregister(subscriptionActor)
      log.debug("Unregistered SubscriptionActor {}", subscriptionActor)
  }

  override def failed: Receive = {
    case _ =>
      log.warning("Service unavailable")
      sender() ! ServiceUnavailable
  }
}


