import akka.actor.{Actor, ActorRef, ActorSystem, Props}

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Success

case class Subscribe(topic: String)
case class Event(topic: String, payload: String)
case class EventAck(topic: String)

class BrokerActor extends Actor {

  private val subscriptions = mutable.HashMap[String, mutable.HashSet[ActorRef]]()
  private val events = Seq[Event]()

  override def receive: Receive = {
    case Subscribe(topic) =>
      subscriptions.getOrElseUpdate(topic, { mutable.HashSet[ActorRef]() }).add(sender())
      println(s"Subscribed $sender() to topic $topic")

    case evt: Event =>
      import context.dispatcher

      val publisher = sender()
      Future { events :+ evt }
          .andThen { case Success(_) =>
            publisher ! EventAck(evt.topic)
            subscriptions.get(evt.topic)
              .foreach { subscribers => subscribers.foreach(s => s ! evt) }
          }
      println(s"Got new event ($evt)")
  }
}


