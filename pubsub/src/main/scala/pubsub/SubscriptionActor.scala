package pubsub

import akka.actor.{ActorRef, Props}
import org.reactivestreams.{Subscriber, Subscription}
import pubsub.SubscriptionActor.EventOrdinal
import pubsub.fsm.FSMActor.OnStateChangedCallback
import pubsub.fsm.FSMActorState.{FSMReceive, actionState}
import pubsub.fsm._

/** Manages subscription requested by a subscriber.
  *
  * At first tries to catch up with event upstream starting with the specified position. Then
  * listens to the topic for new events.
  * @param subscriber Subscriber actor which requested the subscription and which will receive event notifications.
  * @param topic Topic name.
  * @param initialState Initial state of the actor. An event ordinal to start from.
  * @param eventStore Event store to catch up with upstream.
  */
class SubscriptionActor(
       val subscriber: ActorRef,
       val topic: String,
       override val initialState: SubscriptionActor.EventOrdinal,
       val eventStore: EventStore) extends FSMActor[EventOrdinal] {

  protected val Created: FSMActorState[EventOrdinal] = actionState[EventOrdinal]("Created") { () =>
    log.debug(s"Sending SubscriberAck to $subscriber")
    subscriber ! SubscribeAck(self)
  }

  //noinspection TypeAnnotation
  protected val CatchingUpWithUpstream = new FSMActorState[EventOrdinal] {

    override val name: String = "CatchingUpWithUpstream"

    case object CaughtWithUpstream
    case class UpstreamEvent(evt: EventNotification)

    override def onEnter(nextEventOrd: EventOrdinal): StateActionResult[EventOrdinal] = {
      eventStore.eventUpstream(topic, nextEventOrd).subscribe(new Subscriber[EventNotification] {
        override def onError(t: Throwable): Unit = ()
        override def onComplete(): Unit = self ! CaughtWithUpstream
        override def onNext(t: EventNotification): Unit = self ! UpstreamEvent(t)
        override def onSubscribe(s: Subscription): Unit = s.request(Long.MaxValue)
      })

      Stay(nextEventOrd)
    }

    override def receive: FSMReceive[EventOrdinal] = {
      case (nextEventOrd, evt: UpstreamEvent) =>
        notifySubscriber(evt.evt)
        Stay(nextEventOrd + 1)

      case (nextEventOrd, CaughtWithUpstream) =>
        log.info(s"Caught up with upstream ($initialState : $nextEventOrd)")
        Leave(nextEventOrd)
    }
  }

  def notifySubscriber(eventNotification: EventNotification): Unit = {
    log.debug(s"Sending event notification to the subscriber $subscriber")
    subscriber ! eventNotification
  }

  //noinspection TypeAnnotation
  protected val WaitingForEvents = FSMActorState[EventOrdinal]("WaitingForEvents",
    receiveFunc = {
      case (nextEventOrdinal, event: EventNotification) if event.ordinal.equals(nextEventOrdinal) =>
        notifySubscriber(event)
        Stay(nextEventOrdinal + 1)

      case (nextEventOrd, _: EventNotification) =>
        log.info("It seems we missed some of the events.")
        Leave(nextEventOrd)
    })

  import StateFlow._
  val MainLoop: StateFlow[EventOrdinal] = CatchingUpWithUpstream >>: WaitingForEvents >>: loop(MainLoop)
  override val stateFlow: StateFlow[EventOrdinal] = Created >>: MainLoop
}

object SubscriptionActor {

  type EventOrdinal = Long

  def props(
         subscriber: ActorRef,
         topic: String,
         eventOrdinal: EventOrdinal,
         eventStore: EventStore,
         onStateChangedCallback: OnStateChangedCallback = { _ => }): Props =
    Props(new {
      override val onStateChanged: Option[String] => Unit = onStateChangedCallback
    } with SubscriptionActor(subscriber, topic, eventOrdinal, eventStore))
}
