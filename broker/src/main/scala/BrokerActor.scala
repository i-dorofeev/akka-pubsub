import akka.actor.{Actor, ActorRef, ActorSystem, Props}

import scala.collection.mutable

case class Subscribe(topic: String)
case class Event(topic: String, payload: String)

class BrokerActor extends Actor {

  private val subscriptions = mutable.HashMap[String, mutable.HashSet[ActorRef]]()

  override def receive: Receive = {
    case Subscribe(topic) =>
      subscriptions.getOrElseUpdate(topic, { mutable.HashSet[ActorRef]() }).add(sender())
      println(s"Subscribed $sender() to topic $topic")

    case evt: Event =>
      subscriptions.get(evt.topic)
          .foreach { subscribers => subscribers.foreach(s => s ! evt) }
      println(s"Got new event ($evt.topic, $evt.payload)")
  }
}


