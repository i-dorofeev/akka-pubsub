package pubsub.fsm

import akka.actor.{Actor, ActorLogging}
import pubsub.fsm.FSMActorState.FSMReceive

/** Enables an actor to define possible states which it may be in and
  * state transition logic with an clear and easy-to-use API.
  *
  * Allows to pass state data when making a transition from one state to another.
  *
  * Separates state logic from transition logic. A state never knows where
  * to go after leaving itself. Not only does it contributes to clean separation
  * of concepts but it also allows reuse of same state definitions in different
  * parts of state flow.
  *
  * @tparam T Type of state data to pass between states.
  */
trait FSMActor[T] extends Actor with ActorLogging {

  type NextState = (StateFlow[T], T)

  protected val initialState: T

  private var currentFlow: StateFlow[T] = _
  private var currentState: T = initialState

  /** Definition of state flow for this FSMActor */
  protected val stateFlow: StateFlow[T]

  /** Called when state changes.
    *
    * Receives name of the new state or [[None]] if actor's finished its final state.
    * Can be used to track state changes outside the actor for testing and debugging purposes.
    */
  protected val onStateChanged: Option[String] => Unit = { _ => () }

  private def runFlow(launcher: () => Option[NextState]): Unit = {
    launcher() match {
      case Some((flow, stateData)) if flow.equals(currentFlow) && !stateData.equals(currentState) =>
        currentState = stateData

      case Some((flow, stateData)) if !flow.equals(currentFlow) =>
        currentFlow = flow
        currentState = stateData
        log.debug(s"Entered state ${currentFlow.state.name}")
        onStateChanged(Some(currentFlow.state.name))

      case Some(_) =>
        // otherwise do nothing

      case None =>
        context.stop(self)
        log.debug(s"Stopped FSM")
        onStateChanged(None)
    }
  }

  override def preStart(): Unit = runFlow(() => enter(stateFlow, initialState))

  private val stay: FSMReceive[T] = { case (stateData, _) => Stay(stateData) }

  // unhandled messages leave the actor in the current state
  private def handle: FSMReceive[T] = currentFlow.state.receive orElse stay

  override def receive: Receive = {
    case msg =>
      val result = handle.apply((currentState, msg))
      runFlow(() => next(currentFlow, result))
  }

  private def enter(flow: StateFlow[T], stateData: T): Option[NextState] = {
    log.debug(s"Entering state ${flow.state.name}")
    next(flow, flow.state.onEnter(stateData))
  }

  private def next(flow: StateFlow[T], result: StateActionResult[T]): Option[NextState] = {
    result match {
      case Leave(stateData) =>
        log.debug(s"Leaving state ${flow.state.name}")
        flow.state.onExit()
        flow.nextState(flow.state).flatMap { nextState => enter(nextState, stateData) }
      case Stay(stateData) =>
        Some((flow, stateData))
    }
  }
}

object FSMActor {
  type OnStateChangedCallback = Option[String] => Unit
}
