package pubsub

import akka.actor.{ActorRef, Props}
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import pubsub.EventStore.EventUpstream
import pubsub.fsm.FSMActor.OnStateChangedCallback
import pubsub.fsm.FSMActorState.actionState
import pubsub.fsm._


object SubscriptionActor {
  def props(
         subscriber: ActorRef,
         topic: String,
         eventOrdinal: Long,
         eventStore: EventStore,
         onStateChangedCallback: OnStateChangedCallback = { _ => }): Props =
    Props(new SubscriptionActor(subscriber, topic, eventOrdinal, eventStore, onStateChangedCallback))
}

object EventStore {
  type EventUpstream = Publisher[EventNotification]
}

trait EventStore {
  def eventUpstream(topic: String, startFrom: Long): EventUpstream
}


class SubscriptionActor(
       val subscriber: ActorRef,
       val topic: String,
       var eventOrdinal: Long,
       val eventStore: EventStore,
       val onStateChangedCallback: OnStateChangedCallback) extends FSMActor {

  override protected def onStateChanged(newState: Option[String]): Unit = onStateChangedCallback(newState)

  private val Created = actionState("Created") { () =>
    log.debug(s"Sending SubscriberAck to $subscriber")
    subscriber ! SubscribeAck(self)
  }

  private val CatchingUpWithUpstream = new FSMActorState {

    override val name: String = "CatchingUpWithUpstream"

    case object CaughtWithUpstream

    override def onEnter(): StateActionResult = {
      eventStore.eventUpstream(topic, eventOrdinal).subscribe(new Subscriber[EventNotification] {
        override def onError(t: Throwable): Unit = ()
        override def onComplete(): Unit = self ! CaughtWithUpstream
        override def onNext(t: EventNotification): Unit = notifySubscriber(t)
        override def onSubscribe(s: Subscription): Unit = s.request(Long.MaxValue)
      })

      Stay
    }

    override def receive: PartialFunction[Any, StateActionResult] = {
      case CaughtWithUpstream => Leave
    }
  }

  def notifySubscriber(eventNotification: EventNotification): Unit = {
    log.debug(s"Sending event notification to the subscriber $subscriber")
    subscriber ! eventNotification
    eventOrdinal += 1
  }

  private val WaitingForEvents = FSMActorState("WaitingForEvents",
    receiveFunc = {
      case event: EventNotification if event.ordinal == eventOrdinal + 1 =>
        notifySubscriber(event)
        Stay

      case _: EventNotification =>
        // it seems that we missed some of the events
        Leave
    })

  import StateFlow._
  val MainLoop: StateFlow = CatchingUpWithUpstream >>: WaitingForEvents >>: loop(MainLoop)
  override val stateFlow: StateFlow = Created >>: MainLoop
}
