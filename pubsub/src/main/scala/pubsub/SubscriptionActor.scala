package pubsub

import akka.actor.{ActorRef, Props}
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import pubsub.SubscriptionActor.EventUpstream
import pubsub.fsm.FSMActor.OnStateChangedCallback
import pubsub.fsm.FSMActorState.actionState
import pubsub.fsm._


object SubscriptionActor {
  type EventUpstream = Publisher[EventNotification]
  def props(subscriber: ActorRef, upstream: EventUpstream, onStateChangedCallback: OnStateChangedCallback = { _ => }): Props =
    Props(new SubscriptionActor(subscriber, upstream, onStateChangedCallback))
}


class SubscriptionActor(val subscriber: ActorRef, val upstream: EventUpstream, onStateChangedCallback: OnStateChangedCallback) extends FSMActor {

  override protected def onStateChanged(newState: Option[String]): Unit = onStateChangedCallback(newState)

  private val Created = actionState("Created") { () =>
    log.debug(s"Sending SubscriberAck to $subscriber")
    subscriber ! SubscribeAck(self)
  }

  private val CatchingUpWithUpstream = new FSMActorState {

    override val name: String = "CatchingUpWithUpstream"

    case object CaughtWithUpstream

    override def onEnter(): StateActionResult = {
      upstream.subscribe(new Subscriber[EventNotification] {
        override def onError(t: Throwable): Unit = ()
        override def onComplete(): Unit = self ! CaughtWithUpstream
        override def onNext(t: EventNotification): Unit = notifySubscriber(t)
        override def onSubscribe(s: Subscription): Unit = ()
      })

      Stay
    }

    def notifySubscriber(eventNotification: EventNotification): Unit = {
      log.debug(s"Sending event notification to the subscriber $subscriber")
      subscriber ! eventNotification
    }

    override def receive: PartialFunction[Any, StateActionResult] = {
      case CaughtWithUpstream => Leave
    }
  }

  private val WaitingForEvents = FSMActorState("WaitingForEvents",
    receiveFunc = {
      case _ => Stay
    })

  import StateFlow._
  override val stateFlow: StateFlow = Created >>: CatchingUpWithUpstream >>: WaitingForEvents
}
