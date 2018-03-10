package pubsub

import akka.actor.ActorRef

case class SubscribeAck(subscriptionRef: ActorRef)

/** Event notification.
  *
  * @param ordinal The absolute ordinal of the event inside a topic.
  * @param payload Payload of the event.
  */
case class EventNotification(ordinal: Long, payload: String)
