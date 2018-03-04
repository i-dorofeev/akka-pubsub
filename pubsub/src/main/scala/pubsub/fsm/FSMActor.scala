package pubsub.fsm

import akka.actor.Actor

sealed trait StateActionResult
object Stay extends StateActionResult
object Leave extends StateActionResult

trait FSMActorState {

  def onEnter(): StateActionResult = Stay
  def receive: PartialFunction[Any, StateActionResult] = { case _ => Stay }
  def onExit(): Unit = ()

}

object FSMActorState {
  def actionState(action: () => Unit): FSMActorState = new FSMActorState {
    override def onEnter(): StateActionResult = {
      action()
      Leave
    }
  }
}

object StateFlow {

  implicit def flowEnd(state: FSMActorState): StateFlow = new StateFlow(state, _ => None)
}

class StateFlow(val state: FSMActorState, val nextState: FSMActorState => Option[StateFlow]) {

  def enter(): Option[StateFlow] = {
    next(state.onEnter())
  }

  def next(result: StateActionResult = Leave): Option[StateFlow] = {
    result match {
      case Leave =>
        state.onExit()
        nextState(state).flatMap(_.enter())
      case Stay =>
        Some(this)
    }
  }

  def >>:(state: FSMActorState): StateFlow = new StateFlow(state, state => Some(this))
}

/**
  *
  */
trait FSMActor extends Actor {

  var currentFlow: StateFlow = _

  val stateFlow: StateFlow

  override def preStart(): Unit = {
    stateFlow.enter() match {
      case Some(state) => currentFlow = state
      case None => context.stop(self)
    }
  }

  override def receive: Receive = {
    case msg =>
      val stay: PartialFunction[Any, StateActionResult] = { case _ => Stay }
      val result = (currentFlow.state.receive orElse stay)(msg)
      currentFlow.next(result) match {
        case Some(state) => currentFlow = state
        case None => context.stop(self)
      }
  }
}
