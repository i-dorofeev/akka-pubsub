package pubsub.fsm

import akka.actor.{Actor, ActorLogging}
import pubsub.fsm.StateFlow.LoopState

sealed trait StateActionResult
object Stay extends StateActionResult
object Leave extends StateActionResult

/** Defines behaviour of an FSMActor state. */
trait FSMActorState {

  /** The name of the state.
    *
    * Can be used for testing or debugging purposes.
    */
  val name: String = this.toString

  /** Called when entering the state.
    * @return Either [[Stay]] to proceed with the state or [[Leave]] to leave the state immediately.
    */
  def onEnter(): StateActionResult = Stay

  /** Defines the message handler function for this state.
    *
    * For every message the handler should return either [[Stay]] to proceed with the state or [[Leave]] to leave
    * the state immediately.
    */
  def receive: PartialFunction[Any, StateActionResult] = { case _ => Stay }

  /** Called when leaving the state. */
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
            onEnterCallback: () => StateActionResult = { () => Stay },
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

  /** Implicit conversion from [[pubsub.fsm.FSMActorState]] to [[pubsub.fsm.StateFlow]]
    * so that the resulting flow is a last node in the flow.
    * @param state The last [[FSMActorState]] of the flow
    * @return An instance of [[StateFlow]]
    */
  implicit def flowEnd(state: FSMActorState): StateFlow = new StateFlow(state, _ => None)

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
  def loop(flow: => StateFlow): StateFlow = new StateFlow(LoopState, _ => Some(flow))

  object LoopState extends FSMActorState
}

/** Definition of state flow.
  * @param state Initial state
  * @param nextState Function computing the next state
  */
case class StateFlow(state: FSMActorState, nextState: FSMActorState => Option[StateFlow]) {
  def >>:(state: FSMActorState): StateFlow = this match {
    // flow created with StateFlow.loop function
    case StateFlow(LoopState, _) => new StateFlow(state, _ => nextState(LoopState))

    // normal flow
    case _  => new StateFlow(state, _ => Some(this))
  }
}

/** Enables an actor to define possible states which it may be in and
  * state transition logic with an clear and easy-to-use API.
  *
  * Separates state logic from transition logic. A state never knows where
  * to go after leaving itself. Not only does it contributes to clean separation
  * of concepts but it also allows reuse of same state definitions in different
  * parts of state flow.
  */
trait FSMActor extends Actor with ActorLogging {

  private var currentFlow: StateFlow = _

  /** Definition of state flow for this FSMActor */
  protected val stateFlow: StateFlow

  /** Called when state changes.
    *
    * Can be used to track state changes outside the actor for testing and debugging purposes.
    * @param newState Name of the new state.
    */
  protected def onStateChanged(newState: Option[String]): Unit = ()

  private def runFlow(launcher: () => Option[StateFlow]): Unit = {
    launcher() match {
      case Some(flow) if !flow.equals(currentFlow) =>
        currentFlow = flow
        log.debug(s"Entered flow ${currentFlow.state.name}")
        onStateChanged(Some(currentFlow.state.name))

      case Some(flow) if flow.equals(currentFlow) =>
        // do nothing

      case None =>
        context.stop(self)
        log.debug(s"Stopped FSM")
        onStateChanged(None)
    }
  }

  override def preStart(): Unit = runFlow(() => enter(stateFlow))

  private val stay: PartialFunction[Any, StateActionResult] = { case _ => Stay }

  // unhandled messages leave the actor in the current state
  private def handle: Any => StateActionResult = currentFlow.state.receive orElse stay

  override def receive: Receive = {
    case msg =>
      val result = handle(msg)
      runFlow(() => next(currentFlow, result))
  }

  private def enter(flow: StateFlow): Option[StateFlow] = {
    log.debug(s"Entering state ${flow.state.name}")
    next(flow, flow.state.onEnter())
  }

  private def next(flow: StateFlow, result: StateActionResult = Leave): Option[StateFlow] = {
    result match {
      case Leave =>
        log.debug(s"Leaving state ${flow.state.name}")
        flow.state.onExit()
        flow.nextState(flow.state).flatMap { nextState => enter(nextState) }
      case Stay =>
        Some(flow)
    }
  }
}

object FSMActor {
  type OnStateChangedCallback = Option[String] => Unit
}
