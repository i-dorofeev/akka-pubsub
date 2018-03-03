package pubsub.old

import akka.actor.ActorRef

import scala.collection.mutable

/**
  * Subscription registry.
  *
  * Manages an index of topics to subscription actors and
  * an index of subscription actors to a topic allowing quick
  * retrieval of subscriptions by topic as well as quick removal
  * of a subscription when it dies.
  */
class Subscriptions {

  private val subscriptions = mutable.HashMap[String, mutable.HashSet[ActorRef]]()
  private val actors = mutable.HashMap[ActorRef, String]()

  /**
    * Subscribes an actor to a topic.
    * @param topic Topic to subscribe to.
    * @param subscriptionActor Actor that manages this subscription.
    */
  def register(topic: String, subscriptionActor: ActorRef): Unit = {
    subscriptions.getOrElseUpdate(topic, { mutable.HashSet[ActorRef]() })
      .add(subscriptionActor)
    actors += (subscriptionActor -> topic)
  }

  /**
    * Returns a sequence of subscription actors by topic.
    * @param topic Topic
    * @return A sequence of subscription actors.
    */
  def byTopic(topic: String): Seq[ActorRef] = {
    subscriptions.getOrElse(topic, mutable.HashSet.empty).toSeq
  }

  /**
    * Cancels the subscription managed by specified actor.
    * @param subscriptionActor Subscription actor.
    */
  def unregister(subscriptionActor: ActorRef): Unit = {
    for { topic <- actors.remove(subscriptionActor)
          subscriptions <- subscriptions.get(topic) }
      subscriptions -= subscriptionActor
  }
}
