package pubsub.old

import akka.actor.{Props, Terminated}
import com.typesafe.config.{Config, ConfigFactory}
import pubsub.old.BrokerActor.{Event, EventAck, ServiceUnavailable, Subscribe}

import scala.concurrent.Future
import scala.util.Success

/**
  * Companion object for BrokerActor
  */
object BrokerActor {

  /**
    * Subscription command. Initiated by subscriber.
    * @param topic Event topic to subscribe to
    * @param eventId Event ID to start the subscription from
    */
  case class Subscribe(topic: String, eventId: Int)

  /**
    * Carries event info.
    *
    * A publisher sends the broker an Event and the broker forwards it to
    * the subscribers.
    * @param topic Topic that the event is published into
    * @param eventId Event ID
    * @param payload Serializable payload of the event
    */
  case class Event(topic: String, eventId: Int, payload: String)

  /**
    * Event receipt acknowledgement.
    *
    * Sent by the broker back to the publisher as a confirmation that an event
    * is successfully processed.
    *
    * @param topic Topic of the acknowledged event
    */
  case class EventAck(topic: String)

  /**
    * Indicates that the broker service cannot process the message at the moment.
    *
    * When a publisher receives this message, it is supposed to retry sending the event later.
    */
  case class ServiceUnavailable()

  /**
    * Constructs Props object for BrokerActor.
    * @param config Configuration object to load properties from
    * @return Props object for spawning BrokerActor
    */
  def props(config: Config = ConfigFactory.load()): Props = Props(new BrokerActor(config))
}

/**
  * Serves as an entry point for the event bus.
  *
  * At startup it initializes database and in case of failure responds
  * to all messages with ServiceUnavailable.
  *
  * @param config Configuration object to load properties from
  */
class BrokerActor(val config: Config) extends InitializingActor {

  private val subscriptions = new Subscriptions
  private val db = new BrokerDatabase(config)

  import context._

  override def init: Future[Unit] = db.initialize()
  override def postStop: Unit = db.shutdown()

  override def working: Receive = {
    case Subscribe(topic, eventId) =>
      val subscriptionActor = actorOf(SubscriptionActor.props(db, sender(), topic, eventId))
      subscriptions.register(topic, subscriptionActor)
      watch(subscriptionActor) // let's look after the subscription actor so we can unregister it when it's terminated

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


