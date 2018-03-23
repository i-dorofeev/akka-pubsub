package pubsub.fsm

/** Defines behaviour of an FSMActor state. */
trait FSMActorState[T] {

  /** The name of the state.
    *
    * Can be used for testing or debugging purposes.
    * We use `def` here instead of `val` to be able to mock this property in tests.
    */
  def name: String = this.toString

  /** Called when entering the state.
    * @return Either [[pubsub.fsm.Stay]] to proceed with the state or [[pubsub.fsm.Leave]] to leave the state immediately.
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

  /** Constructs a state which executes the specified action on enter
    * and immediately exits the state.
    *
    * @param stateName Name of the state.
    * @param action Action to execute when entering the state.
    * @tparam T Type parameter of the state.
    * @return Instance of the constructed state.
    */
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

