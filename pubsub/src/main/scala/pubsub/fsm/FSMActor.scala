package pubsub.fsm

import akka.actor.{Actor, ActorLogging}
import pubsub.fsm.FSMActorState.FSMReceive
import pubsub.fsm.StateFlow.LoopState

sealed trait StateActionResult[A]
case class Stay[A](stateData: A) extends StateActionResult[A]
case class Leave[A](stateData: A) extends StateActionResult[A]

/** Defines behaviour of an FSMActor state. */
trait FSMActorState[T] {

  /** The name of the state.
    *
    * Can be used for testing or debugging purposes.
    */
  val name: String = this.toString

  /** Called when entering the state.
    * @return Either [[Stay]] to proceed with the state or [[Leave]] to leave the state immediately.
    */
  def onEnter(stateData: T): StateActionResult[T] = Stay(stateData)

  /** Defines the message handler function for this state.
    *
    * For every message the handler should return either [[Stay]] to proceed with the state or [[Leave]] to leave
    * the state immediately.
    */
  def receive: PartialFunction[(T, Any), StateActionResult[T]] = { case (stateData, _) => Stay(stateData) }

  /** Called when leaving the state. */
  def onExit(): Unit = ()

  override def toString: String = name
}

object FSMActorState {

  type Msg[T] = (T, Any)
  type FSMReceive[T] = PartialFunction[Msg[T], StateActionResult[T]]

  def actionState[T](stateName: String)(action: () => Unit): FSMActorState[T] = new FSMActorState[T] {
    override val name: String = stateName
    override def onEnter(stateData: T): StateActionResult[T] = {
      action()
      Leave(stateData)
    }
  }

  def apply[T](stateName: String,
            onEnterCallback: T => StateActionResult[T] = { stateData:T => Stay(stateData) },
            receiveFunc: FSMReceive[T],
            onExitCallback: () => Unit = { () => Unit }
         ): FSMActorState[T] = new FSMActorState[T] {
    override val name: String = stateName
    override def onEnter(stateData: T): StateActionResult[T] = { onEnterCallback(stateData) }
    override def receive: FSMReceive[T] = receiveFunc
    override def onExit(): Unit = { onExitCallback() }
  }
}

object StateFlow {

  import scala.language.implicitConversions

  /** Implicit conversion from [[pubsub.fsm.FSMActorState]] to [[pubsub.fsm.StateFlow]]
    * so that the resulting flow is a last node in the flow.
    * @param state The last [[FSMActorState]] of the flow
    * @return An instance of [[StateFlow]]
    */
  implicit def flowEnd[T](state: FSMActorState[T]): StateFlow[T] = new StateFlow(state, _ => None)

  /** Creates StateFlow for a yet undefined value.
    *
    * Enables syntax like that:
    * {{{ val MainLoop = State1 >>: State2 >>: State3 >>: loop(MainLoop) }}}
    *
    * `MainLoop` is undefined when evaluating the expression but since it is passed by name to `loop` function,
    * we can safely use it.
    *
    * @param flow Passed-by-name flow
    * @return An instance of [[StateFlow]]
    */
  def loop[T](flow: => StateFlow[T]): StateFlow[T] = new StateFlow(LoopState(), _ => Some(flow))

  case class LoopState[T]() extends FSMActorState[T]
}

/** Definition of state flow.
  * @param state Initial state
  * @param nextState Function computing the next state
  */
case class StateFlow[T](state: FSMActorState[T], nextState: FSMActorState[T] => Option[StateFlow[T]]) {
  def >>:(state: FSMActorState[T]): StateFlow[T] = this match {
    // flow created with StateFlow.loop function
    case StateFlow(loopState: LoopState[T], _) => new StateFlow(state, _ => nextState(loopState))

    // normal flow
    case _  => new StateFlow(state, _ => Some(this))
  }
}

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
    * Can be used to track state changes outside the actor for testing and debugging purposes.
    * @param newState Name of the new state.
    */
  protected def onStateChanged(newState: Option[String]): Unit = ()

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
