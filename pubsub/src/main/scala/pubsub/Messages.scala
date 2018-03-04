package pubsub

import akka.actor.ActorRef

case class SubscribeAck(subscriptionRef: ActorRef)
case class EventNotification(payload: String)
