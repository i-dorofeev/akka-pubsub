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

/**
  *
  */
trait FSMActor extends Actor {

  var currentState: FSMActorState = _

  override def preStart(): Unit = {
    currentState = enterState(initialState())
  }

  private def enterState(implicit newState: FSMActorState): FSMActorState = processResult(newState, newState.onEnter())

  private def processResult(currentState: FSMActorState, result: StateActionResult): FSMActorState = result match {
    case Leave =>
      leaveState(currentState)

    case Stay =>
      context.become(receive(currentState), discardOld = true)
      currentState
  }

  private def receive(state: FSMActorState): Receive = {
    case msg =>
      val stay: PartialFunction[Any, StateActionResult] = { case _ => Stay }
      val result = (state.receive orElse stay)(msg)
      currentState = processResult(state, result)
  }

  private def leaveState(state: FSMActorState): FSMActorState = {
    state.onExit()
    transition.andThen(s => enterState(s)).apply(state)
  }

  protected def transition: PartialFunction[FSMActorState, FSMActorState]

  override def receive: Receive = {
    case _ => throw new UnsupportedOperationException()
  }

  protected def initialState(): FSMActorState
}
