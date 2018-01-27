import akka.actor.{Actor, Props, Stash, Terminated}

import scala.util.{Failure, Success}

case class Subscribe(topic: String, eventId: Int)
case class Event(topic: String, eventId: Int, payload: String)
case class EventAck(topic: String)

object BrokerActor {
  def props(): Props = Props(new BrokerActor())
}

class BrokerActor extends Actor with Stash {

  private val subscriptions = new Subscriptions

  import context._

  override def preStart(): Unit = {
    BrokerDatabase.initialize()
        .onComplete {
          case Success(_) =>
            self ! "database initialized"
            println("Database initialized")
          case Failure(ex) =>
            println(s"Failed to initialize database: $ex")
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
      println("Switched to work mode")

    case msg =>
      stash()
      println(s"Stashed $msg")
  }

  private def work: Receive = {
    case Subscribe(topic, eventId) =>
      val subscriptionActor = actorOf(Props(classOf[SubscriptionActor], sender(), topic, eventId))
      subscriptions.register(topic, subscriptionActor)
      watch(subscriptionActor)

      println(s"Subscribed $sender() to topic $topic")

    case evt: Event =>
      val publisher = sender()
      BrokerDatabase.persistEvent(evt)
          .andThen { case Success(_) =>
            publisher ! EventAck(evt.topic)
            subscriptions.byTopic(evt.topic).foreach { s => s ! evt }
          }
      println(s"Got new event ($evt)")

    case Terminated(subscriptionActor) =>
      subscriptions.unregister(subscriptionActor)
      println(s"Unregistered SubscriptionActor $subscriptionActor")
  }
}


