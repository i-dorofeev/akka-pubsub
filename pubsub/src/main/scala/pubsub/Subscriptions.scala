package pubsub

import akka.actor.ActorRef

import scala.collection.mutable

class Subscriptions {

  private val subscriptions = mutable.HashMap[String, mutable.HashSet[ActorRef]]()
  private val actors = mutable.HashMap[ActorRef, String]()

  def register(topic: String, subscriptionActor: ActorRef): Unit = {
    subscriptions.getOrElseUpdate(topic, { mutable.HashSet[ActorRef]() })
      .add(subscriptionActor)
    actors += (subscriptionActor -> topic)
  }

  def byTopic(topic: String): Seq[ActorRef] = {
    subscriptions.getOrElse(topic, mutable.HashSet.empty).toSeq
  }

  def unregister(subscriptionActor: ActorRef): Unit = {
    for { topic <- actors.remove(subscriptionActor)
          subscriptions <- subscriptions.get(topic) }
      subscriptions -= subscriptionActor
  }
}
