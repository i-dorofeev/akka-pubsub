package pubsub.fsm

import pubsub.fsm.StateFlow.LoopState

/** Definition of state flow.
  * @param state Initial state
  * @param nextState Function computing the next state
  */
case class StateFlow[T](state: FSMActorState[T], nextState: FSMActorState[T] => Option[StateFlow[T]]) {

  /** Chains this StateFlow with the specified [[FSMActorState]] making this StateFlow next in the chain.
    *
    * @param state State to preceed this StateFlow in the flow.
    * @return New [[StateFlow]] instance.
    */
  def >>:(state: FSMActorState[T]): StateFlow[T] = this match {
    // flow created with StateFlow.loop function
    case StateFlow(loopState: LoopState[T], _) => new StateFlow(state, _ => nextState(loopState))

    // normal flow
    case _  => new StateFlow(state, _ => Some(this))
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
