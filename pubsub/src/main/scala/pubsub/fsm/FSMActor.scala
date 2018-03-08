package pubsub.fsm

import akka.actor.Actor

sealed trait StateActionResult
object Stay extends StateActionResult
object Leave extends StateActionResult

trait FSMActorState {

  /**
    * The name of the state.
    * Can be used for testing or debugging purposes.
    */
  val name: String = this.toString

  def onEnter(): StateActionResult = Stay
  def receive: PartialFunction[Any, StateActionResult] = { case _ => Stay }
  def onExit(): Unit = ()

}

object FSMActorState {
  def actionState(stateName: String)(action: () => Unit): FSMActorState = new FSMActorState {
    override val name: String = stateName
    override def onEnter(): StateActionResult = {
      action()
      Leave
    }
  }

  def apply(stateName: String,
            onEnterCallback: () => StateActionResult = { () => Leave },
            receiveFunc: PartialFunction[Any, StateActionResult],
            onExitCallback: () => Unit = { () => Unit }
         ): FSMActorState = new FSMActorState {
    override val name: String = stateName
    override def onEnter(): StateActionResult = { onEnterCallback() }
    override def receive: PartialFunction[Any, StateActionResult] = receiveFunc
    override def onExit(): Unit = { onExitCallback() }
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

  /**
    * Called when state changes.
    * Can be used to track state changes outside the actor for testing and debugging purposes.
    * @param newState Name of the new state.
    */
  protected def onStateChanged(newState: Option[String]): Unit = ()

  private def runFlow(launcher: () => Option[StateFlow]): Unit = {
    launcher() match {
      case Some(state) =>
        currentFlow = state
        onStateChanged(Some(currentFlow.state.name))

      case None =>
        context.stop(self)
        onStateChanged(None)
    }
  }

  override def preStart(): Unit = runFlow(() => stateFlow.enter())

  private val stay: PartialFunction[Any, StateActionResult] = { case _ => Stay }

  // unhandled messages leave the actor in the current state
  private def handle: Any => StateActionResult = currentFlow.state.receive orElse stay

  override def receive: Receive = {
    case msg =>
      val result = handle(msg)
      runFlow(() => currentFlow.next(result))
  }
}
