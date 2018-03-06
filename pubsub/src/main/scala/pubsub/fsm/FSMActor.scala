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

  import scala.language.implicitConversions
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

  def >>:(state: FSMActorState): StateFlow = new StateFlow(state, _ => Some(this))
}

/**
  * Enables an actor to define possible states which it may be in and
  * state transition logic with an clear and easy-to-use API.
  *
  * Separates state logic from transition logic. A state never knows where
  * to go after leaving itself. Not only does it contributes to clean separation
  * of concepts but it also allows reuse of same state definitions in different
  * parts of state flow.
  */
trait FSMActor extends Actor {

  private var currentFlow: StateFlow = _

  protected val stateFlow: StateFlow

  private def runFlow(launcher: () => Option[StateFlow]): Unit = {
    launcher() match {
      case Some(state) => currentFlow = state
      case None => context.stop(self)
    }
  }

  override def preStart(): Unit = runFlow(() => stateFlow.enter())

  private val stay: PartialFunction[Any, StateActionResult] = { case _ => Stay }
  private def handle: Any => StateActionResult = currentFlow.state.receive orElse stay

  override def receive: Receive = {
    case msg =>
      val result = handle(msg)
      runFlow(() => currentFlow.next(result))
  }
}
