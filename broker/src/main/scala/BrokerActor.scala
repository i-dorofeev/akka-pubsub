import akka.actor.{Actor, ActorRef, Stash}

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.{Failure, Success}

case class Subscribe(topic: String)
case class Event(topic: String, payload: String)
case class EventAck(topic: String)

class BrokerActor extends Actor with Stash {

  private val subscriptions = mutable.HashMap[String, mutable.HashSet[ActorRef]]()

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
    case Subscribe(topic) =>
      subscriptions.getOrElseUpdate(topic, { mutable.HashSet[ActorRef]() }).add(sender())
      println(s"Subscribed $sender() to topic $topic")

    case evt: Event =>
      val publisher = sender()
      BrokerDatabase.persistEvent(evt)
          .andThen { case Success(_) =>
            publisher ! EventAck(evt.topic)
            subscriptions.get(evt.topic)
              .foreach { subscribers => subscribers.foreach(s => s ! evt) }
          }
      println(s"Got new event ($evt)")
  }
}


