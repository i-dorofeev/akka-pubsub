package pubsub

import akka.actor.{ActorRef, Props}
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import pubsub.SubscriptionActor.EventUpstream
import pubsub.fsm.FSMActorState.actionState
import pubsub.fsm._


object SubscriptionActor {
  type EventUpstream = Publisher[EventNotification]
  def props(subscriber: ActorRef, upstream: EventUpstream): Props = Props(new SubscriptionActor(subscriber, upstream))
}



class SubscriptionActor(val subscriber: ActorRef, val upstream: EventUpstream) extends FSMActor {

  private val Created = actionState { () => subscriber ! SubscribeAck(self) }

  private val CatchingUpWithUpstream = new FSMActorState {

    case object CaughtWithUpstream

    override def onEnter: StateActionResult = {
      upstream.subscribe(new Subscriber[EventNotification] {
        override def onError(t: Throwable): Unit = ???
        override def onComplete(): Unit = self ! CaughtWithUpstream
        override def onNext(t: EventNotification): Unit = subscriber ! t
        override def onSubscribe(s: Subscription): Unit = ???
      })

      Stay
    }

    override def receive: PartialFunction[Any, StateActionResult] = {
      case CaughtWithUpstream => Leave
    }
  }

  private val WaitingForEvents = new FSMActorState {}

  override def initialState(): FSMActorState = Created

  override def transition: PartialFunction[FSMActorState, FSMActorState] = {
    case Created => CatchingUpWithUpstream
    case CatchingUpWithUpstream => WaitingForEvents
  }
}
